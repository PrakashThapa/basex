package org.basex.server;

import static org.basex.core.Text.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import org.basex.BaseXServer;
import org.basex.core.CommandParser;
import org.basex.core.Context;
import org.basex.core.Main;
import org.basex.core.Process;
import org.basex.core.Prop;
import org.basex.core.proc.Close;
import org.basex.core.proc.Exit;
import org.basex.core.proc.IntInfo;
import org.basex.core.proc.IntOutput;
import org.basex.core.proc.IntStop;
import org.basex.data.Data;
import org.basex.io.BufferedOutput;
import org.basex.io.IO;
import org.basex.io.PrintOutput;
import org.basex.query.QueryException;
import org.basex.util.Performance;

/**
 * Single session for a client-server connection.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-09, ISC License
 * @author Andreas Weiler
 */
public final class ServerSession extends Thread {
  /** Database context. */
  private final Context context;
  /** Socket reference. */
  private final Socket socket;
  /** Server reference. */
  private final BaseXServer server;
  /** Core. */
  private Process core;
  /** Timeout thread. */
  private Thread timeout;
  /** Info flag. */
  private boolean info;

  /**
   * Constructor.
   * @param s socket
   * @param b server reference
   * @param i info flag
   */
  public ServerSession(final Socket s, final BaseXServer b, final boolean i) {
    context = new Context(b.context);
    socket = s;
    server = b;
    info = i;
    start();
  }

  @Override
  public void run() {
    try {
      // get command and arguments
      final DataInputStream dis = new DataInputStream(socket.getInputStream());
      final PrintOutput out = new PrintOutput(
          new BufferedOutput(socket.getOutputStream()));

      if(info) Main.outln(this + " Login.");

      while(true) {
        String in = null;
        try {
          in = dis.readUTF();
        } catch(final IOException ex) {
          // this exception is thrown for each session if the server is stopped
          exit();
          break;
        }

        // parse input and create process instance
        final Performance perf = new Performance();
        Process proc = null;
        try {
          proc = new CommandParser(in, context, true).parse()[0];

          if(proc instanceof IntStop || proc instanceof Exit) {
            send(out, true);
            exit();
            if(proc instanceof IntStop) server.quit(false);
            break;
          } else if(proc instanceof IntOutput || proc instanceof IntInfo) {
            if(core == null) {
              out.print(Main.info(SERVERTIME));
            } else if(proc instanceof IntOutput) {
              core.output(out);
              out.write(new byte[IO.BLOCKSIZE]);
            } else {
              new DataOutputStream(out).writeUTF(core.info());
            }
            out.flush();
          } else {
            core = proc;
            timeout(proc);
            send(out, proc.execute(context));
            if(proc.info().equals(PROGERR)) proc.error(SERVERTIME);
            if(timeout != null) timeout.interrupt();
          }
        } catch(final QueryException ex) {
          // invalid command was sent by a client; create empty process
          // with error feedback
          proc = new Process(0) {};
          proc.error(ex.extended());
          core = proc;
          send(out, false);
        }
        if(info) Main.outln(this + " " + in + ": " + perf.getTimer());
      }

      if(info) Main.outln(this + " Logout.");
    } catch(final IOException ex) {
      Main.error(ex, false);
    }
  }

  /**
   * Sends the success flag to the client.
   * @param out output stream
   * @param ok success flag
   * @throws IOException I/O exception
   */
  private void send(final PrintOutput out, final boolean ok)
      throws IOException {
    out.write(ok ? 0 : 1);
    out.flush();
  }

  /**
   * Times out a process.
   * @param proc process reference
   */
  private void timeout(final Process proc) {
    final long to = context.prop.num(Prop.TIMEOUT);
    if(to == 0) return;

    timeout = new Thread() {
      @Override
      public void run() {
        Performance.sleep(to * 1000);
        proc.stop();
      }
    };
    timeout.start();
  }

  /**
   * Exits the session.
   */
  public void exit() {
    stopProcess();
    if(timeout != null) timeout.interrupt();

    context.delete(this);
    new Close().execute(context);

    try {
      socket.close();
    } catch(final IOException ex) {
      Main.error(ex, false);
    }
  }

  /**
   * Stops the current process.
   */
  public void stopProcess() {
    if(core != null) core.stop();
  }

  /**
   * Returns session information.
   * @return database information
   */
  public String info() {
    final Data data = context.data();
    return this + (data != null ? ": " + data.meta.name : "");
  }
  
  @Override
  public String toString() {
    final String host = socket.getInetAddress().getHostAddress();
    final int port = socket.getPort();
    return Main.info("[%:%]", host, port);
  }
}
