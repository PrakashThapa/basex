package org.basex.gui.text;

import static org.basex.gui.layout.BaseXKeys.*;
import static org.basex.util.Token.*;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.util.*;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.*;

import org.basex.core.*;
import org.basex.gui.*;
import org.basex.gui.GUIConstants.Fill;
import org.basex.gui.dialog.*;
import org.basex.gui.layout.*;
import org.basex.io.*;
import org.basex.util.*;

/**
 * Renders and provides edit capabilities for text.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Christian Gruen
 */
public class TextPanel extends BaseXPanel {
  /** Delay for highlighting an error. */
  private static final int ERROR_DELAY = 500;

  /** Search direction. */
  public enum SearchDir {
    /** Current hit. */
    CURRENT,
    /** Next hit. */
    FORWARD,
    /** Previous hit. */
    BACKWARD,
  }

  /** Editor action. */
  public enum Action {
    /** Check for changes; do nothing if input has not changed. */
    CHECK,
    /** Enforce parsing of input. */
    PARSE,
    /** Enforce execution of input. */
    EXECUTE,
    /** Enforce testing of input. */
    TEST
  }

  /** Text editor. */
  protected final TextEditor editor;
  /** Undo history. */
  public final History hist;
  /** Search bar. */
  protected SearchBar search;

  /** Renderer reference. */
  private final TextRenderer rend;
  /** Scrollbar reference. */
  private final BaseXScrollBar scroll;
  /** Editable flag. */
  private final boolean editable;
  /** Link listener. */
  private LinkListener linkListener;

  /**
   * Default constructor.
   * @param edit editable flag
   * @param win parent window
   */
  public TextPanel(final boolean edit, final Window win) {
    this(EMPTY, edit, win);
  }

  /**
   * Default constructor.
   * @param txt initial text
   * @param edit editable flag
   * @param win parent window
   */
  public TextPanel(final byte[] txt, final boolean edit, final Window win) {
    super(win);
    editable = edit;
    editor = new TextEditor(gui);

    setFocusable(true);
    setFocusTraversalKeysEnabled(!edit);

    addMouseMotionListener(this);
    addMouseWheelListener(this);
    addComponentListener(this);
    addMouseListener(this);
    addKeyListener(this);

    addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(final FocusEvent e) {
        if(isEnabled()) caret(true);
      }
      @Override
      public void focusLost(final FocusEvent e) {
        caret(false);
        rend.caret(false);
      }
    });

    setFont(GUIConstants.dmfont);
    layout(new BorderLayout());

    scroll = new BaseXScrollBar(this);
    rend = new TextRenderer(editor, scroll, editable, gui);

    add(rend, BorderLayout.CENTER);
    add(scroll, BorderLayout.EAST);

    setText(txt);
    hist = new History(edit ? editor.text() : null);

    if(edit) {
      setBackground(Color.white);
      setBorder(new MatteBorder(1, 1, 0, 0, GUIConstants.color(6)));
    } else {
      mode(Fill.NONE);
    }

    new BaseXPopup(this, edit ?
      new GUICommand[] {
        new FindCmd(), new FindNextCmd(), new FindPrevCmd(), null, new GotoCmd(), null,
        new UndoCmd(), new RedoCmd(), null,
        new AllCmd(), new CutCmd(), new CopyCmd(), new PasteCmd(), new DelCmd() } :
      new GUICommand[] {
        new FindCmd(), new FindNextCmd(), new FindPrevCmd(), null, new GotoCmd(), null,
        new AllCmd(), new CopyCmd() }
    );
  }

  /**
   * Sets the output text.
   * @param t output text
   */
  public void setText(final String t) {
    setText(token(t));
  }

  /**
   * Sets the output text.
   * @param t output text
   */
  public void setText(final byte[] t) {
    setText(t, t.length);
    resetError();
  }

  /**
   * Returns a currently marked string if it does not extend over more than one line.
   * @return search string or {@code null}
   */
  public String searchString() {
    final String string = editor.copy();
    return string.isEmpty() || string.contains("\n") ? null : string;
  }

  /**
   * Returns the line and column of the current caret position.
   * @return line/column
   */
  public final int[] pos() {
    return rend.pos();
  }

  /**
   * Sets the output text.
   * @param t output text
   * @param s text size
   */
  public final void setText(final byte[] t, final int s) {
    byte[] txt = t;
    if(Token.contains(t, '\r')) {
      // remove carriage returns
      int ns = 0;
      for(int r = 0; r < s; ++r) {
        final byte b = t[r];
        if(b != '\r') t[ns++] = b;
      }
      // new text is different...
      txt = Arrays.copyOf(t, ns);
    }
    if(editor.text(txt)) {
      if(hist != null) hist.store(txt, editor.pos(), 0);
    }
    componentResized(null);
  }

  /**
   * Sets a syntax highlighter, based on the file format.
   * @param file file reference
   * @param opened indicates if file was opened from disk
   */
  protected final void setSyntax(final IO file, final boolean opened) {
    setSyntax(!opened || file.hasSuffix(IO.XQSUFFIXES) ? new SyntaxXQuery() :
      file.hasSuffix(IO.JSONSUFFIX) ? new SyntaxJSON() :
      file.hasSuffix(IO.XMLSUFFIXES) || file.hasSuffix(IO.HTMLSUFFIXES) ||
      file.hasSuffix(IO.XSLSUFFIXES) || file.hasSuffix(IO.BXSSUFFIX) ?
      new SyntaxXML() : Syntax.SIMPLE);
  }

  /**
   * Returns the editable flag.
   * @return boolean result
   */
  public final boolean isEditable() {
    return editable;
  }

  /**
   * Sets a syntax highlighter.
   * @param syntax syntax reference
   */
  public final void setSyntax(final Syntax syntax) {
    rend.setSyntax(syntax);
  }

  /**
   * Sets the caret to the specified position. A text selection will be removed.
   * @param pos caret position
   */
  public final void setCaret(final int pos) {
    editor.pos(pos);
    cursorCode.invokeLater(1);
    caret(true);
  }

  /**
   * Returns the current text cursor.
   * @return cursor position
   */
  private int getCaret() {
    return editor.pos();
  }

  /**
   * Jumps to the end of the text.
   */
  public final void scrollToEnd() {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        editor.pos(editor.size());
        cursorCode.execute(2);
      }
    });
  }

  /**
   * Returns the output text.
   * @return output text
   */
  public final byte[] getText() {
    return editor.text();
  }

  /**
   * Tests if text has been selected.
   * @return result of check
   */
  public final boolean selected() {
    return editor.selected();
  }

  @Override
  public final void setFont(final Font f) {
    super.setFont(f);
    if(rend != null) {
      rend.setFont(f);
      scrollCode.invokeLater(true);
    }
  }

  /** Thread counter. */
  private int errorID;

  /**
   * Removes the error marker.
   */
  public final void resetError() {
    ++errorID;
    editor.error(-1);
    rend.repaint();
  }

  /**
   * Sets the error marker.
   * @param pos start of optional error mark
   */
  public final void error(final int pos) {
    final int eid = ++errorID;
    editor.error(pos);
    new Thread() {
      @Override
      public void run() {
        Performance.sleep(ERROR_DELAY);
        if(eid == errorID) rend.repaint();
      }
    }.start();
  }

  /**
   * Adds or removes a comment.
   */
  public void comment() {
    final int caret = editor.pos();
    if(editor.comment(rend.getSyntax())) hist.store(editor.text(), caret, editor.pos());
    scrollCode.invokeLater(true);
  }

  /**
   * Sorts text.
   */
  public void sort() {
    final DialogSort ds = new DialogSort(gui);
    if(!ds.ok()) return;

    final int caret = editor.pos();
    if(editor.sort()) hist.store(editor.text(), caret, editor.pos());
    scrollCode.invokeLater(true);
  }

  /**
   * Formats the selected text.
   */
  public void format() {
    final int caret = editor.pos();
    if(editor.format(rend.getSyntax())) hist.store(editor.text(), caret, editor.pos());
    scrollCode.invokeLater(true);
  }

  @Override
  public final void setEnabled(final boolean enabled) {
    super.setEnabled(enabled);
    rend.setEnabled(enabled);
    scroll.setEnabled(enabled);
    caret(enabled);
  }

  /**
   * Selects the whole text.
   */
  private void selectAll() {
    editor.select(0, editor.size());
    rend.repaint();
  }

  // SEARCH OPERATIONS ==================================================================

  /**
   * Installs a link listener.
   * @param ll link listener
   */
  public final void setLinkListener(final LinkListener ll) {
    linkListener = ll;
  }

  /**
   * Installs a search bar.
   * @param s search bar
   */
  final void setSearch(final SearchBar s) {
    search = s;
  }

  /**
   * Returns the search bar.
   * @return search bar
   */
  public final SearchBar getSearch() {
    return search;
  }

  /**
   * Performs a search.
   * @param sc search context
   * @param jump jump to next hit
   */
  final void search(final SearchContext sc, final boolean jump) {
    try {
      rend.search(sc);
      gui.status.setText(sc.search.isEmpty() ? Text.OK : Util.info(Text.STRINGS_FOUND_X,  sc.nr()));
      if(jump) jump(SearchDir.CURRENT, false);
    } catch(final Exception ex) {
      final String msg = Util.message(ex).replaceAll(Prop.NL + ".*", "");
      gui.status.setError(Text.REGULAR_EXPR + Text.COLS + msg);
    }
  }

  /**
   * Replaces the text.
   * @param rc replace context
   */
  final void replace(final ReplaceContext rc) {
    try {
      final int[] select = rend.replace(rc);
      if(rc.text != null) {
        final boolean sel = editor.selected();
        setText(rc.text);
        editor.select(select[0], select[sel ? 1 : 0]);
        release(Action.CHECK);
      }
      gui.status.setText(Util.info(Text.STRINGS_REPLACED));
    } catch(final Exception ex) {
      final String msg = Util.message(ex).replaceAll(Prop.NL + ".*", "");
      gui.status.setError(Text.REGULAR_EXPR + Text.COLS + msg);
    }
  }

  /**
   * Jumps to the current, next or previous search string.
   * @param dir search direction
   * @param select select hit
   */
  protected final void jump(final SearchDir dir, final boolean select) {
    // updates the visible area
    final int y = rend.jump(dir, select);
    final int h = getHeight();
    final int p = scroll.pos();
    final int m = y + rend.fontHeight() * 3 - h;
    if(y != -1 && (p < m || p > y)) scroll.pos(y - h / 2);
    rend.repaint();
  }

  // MOUSE INTERACTIONS =================================================================

  @Override
  public final void mouseEntered(final MouseEvent e) {
    gui.cursor(GUIConstants.CURSORTEXT);
  }

  @Override
  public final void mouseExited(final MouseEvent e) {
    gui.cursor(GUIConstants.CURSORARROW);
  }

  @Override
  public final void mouseMoved(final MouseEvent e) {
    if(linkListener == null) return;
    final TextIterator iter = rend.jump(e.getPoint());
    gui.cursor(iter.link() != null ? GUIConstants.CURSORHAND : GUIConstants.CURSORARROW);
  }

  @Override
  public void mouseReleased(final MouseEvent e) {
    if(linkListener == null) return;

    if(SwingUtilities.isLeftMouseButton(e)) {
      editor.endSelection();
      // evaluate link
      if(!editor.selected()) {
        final TextIterator iter = rend.jump(e.getPoint());
        final String link = iter.link();
        if(link != null) linkListener.linkClicked(link);
      }
    }
  }

  @Override
  public void mouseClicked(final MouseEvent e) {
    if(!SwingUtilities.isMiddleMouseButton(e)) return;
    new PasteCmd().execute(gui);
  }

  @Override
  public final void mouseDragged(final MouseEvent e) {
    if(!SwingUtilities.isLeftMouseButton(e)) return;

    // selection mode
    select(e.getPoint(), false);
    final int y = Math.max(20, Math.min(e.getY(), getHeight() - 20));
    if(y != e.getY()) scroll.pos(scroll.pos() + e.getY() - y);
  }

  @Override
  public final void mousePressed(final MouseEvent e) {
    if(!isEnabled() || !isFocusable()) return;

    requestFocusInWindow();
    caret(true);

    if(SwingUtilities.isMiddleMouseButton(e)) copy();

    final boolean shift = e.isShiftDown();
    final boolean selected = editor.selected();
    if(SwingUtilities.isLeftMouseButton(e)) {
      final int c = e.getClickCount();
      if(c == 1) {
        // selection mode
        if(shift) editor.startSelection(true);
        select(e.getPoint(), !shift);
      } else if(c == 2) {
        editor.selectWord();
      } else {
        editor.selectLine();
      }
    } else if(!selected) {
      select(e.getPoint(), true);
    }
  }

  /**
   * Selects the text at the specified position.
   * @param point mouse position
   * @param start states if selection has just been started
   */
  private void select(final Point point, final boolean start) {
    editor.select(rend.jump(point).pos(), start);
    rend.repaint();
  }

  // KEY INTERACTIONS =======================================================

  /**
   * Invokes special keys.
   * @param e key event
   * @return {@code true} if special key was processed
   */
  private boolean specialKey(final KeyEvent e) {
    if(PREVTAB.is(e)) {
      gui.editor.tab(false);
    } else if(NEXTTAB.is(e)) {
      gui.editor.tab(true);
    } else if(CLOSETAB.is(e)) {
      gui.editor.close(null);
    } else if(search != null && ESCAPE.is(e)) {
      search.deactivate(true);
    } else {
      return false;
    }
    e.consume();
    return true;
  }

  @Override
  public void keyPressed(final KeyEvent e) {
    // ignore modifier keys
    if(specialKey(e) || modifier(e)) return;

    // re-animate cursor
    caret(true);

    // operations without cursor movement...
    final int fh = rend.fontHeight();
    if(SCROLLDOWN.is(e)) {
      scroll.pos(scroll.pos() + fh);
      return;
    }
    if(SCROLLUP.is(e)) {
      scroll.pos(scroll.pos() - fh);
      return;
    }

    // set cursor position
    final boolean selected = editor.selected();
    final int pos = editor.pos();

    final boolean shift = e.isShiftDown();
    boolean down = true, consumed = true;

    // move caret
    int lc = Integer.MIN_VALUE;
    final byte[] txt = editor.text();
    if(NEXTWORD.is(e)) {
      editor.nextWord(shift);
    } else if(PREVWORD.is(e)) {
      editor.prevWord(shift);
      down = false;
    } else if(TEXTSTART.is(e)) {
      editor.textStart(shift);
      down = false;
    } else if(TEXTEND.is(e)) {
      editor.textEnd(shift);
    } else if(LINESTART.is(e)) {
      editor.lineStart(shift);
      down = false;
    } else if(LINEEND.is(e)) {
      editor.lineEnd(shift);
    } else if(PREVPAGE_RO.is(e) && !hist.active()) {
      lc = editor.linesUp(getHeight() / fh, false, lastCol);
      down = false;
    } else if(NEXTPAGE_RO.is(e) && !hist.active()) {
      lc = editor.linesDown(getHeight() / fh, false, lastCol);
    } else if(PREVPAGE.is(e) && !BaseXKeys.sc(e)) {
      lc = editor.linesUp(getHeight() / fh, shift, lastCol);
      down = false;
    } else if(NEXTPAGE.is(e) && !BaseXKeys.sc(e)) {
      lc = editor.linesDown(getHeight() / fh, shift, lastCol);
    } else if(NEXTLINE.is(e) && !MOVEDOWN.is(e)) {
      lc = editor.linesDown(1, shift, lastCol);
    } else if(PREVLINE.is(e) && !MOVEUP.is(e)) {
      lc = editor.linesUp(1, shift, lastCol);
      down = false;
    } else if(NEXTCHAR.is(e)) {
      editor.next(shift);
    } else if(PREVCHAR.is(e)) {
      editor.previous(shift);
      down = false;
    } else {
      consumed = false;
    }
    lastCol = lc == Integer.MIN_VALUE ? -1 : lc;

    // edit text
    if(hist.active()) {
      if(MOVEDOWN.is(e)) {
        editor.move(true);
      } else if(MOVEUP.is(e)) {
        editor.move(false);
      } else if(COMPLETE.is(e)) {
        editor.complete();
      } else if(DELLINE.is(e)) {
        editor.deleteLine();
      } else if(DELNEXTWORD.is(e)) {
        editor.deleteNext(true);
      } else if(DELLINEEND.is(e)) {
        editor.deleteNext(false);
      } else if(DELNEXT.is(e)) {
        editor.delete();
      } else if(DELPREVWORD.is(e)) {
        editor.deletePrev(true);
        down = false;
      } else if(DELLINESTART.is(e)) {
        editor.deletePrev(false);
        down = false;
      } else if(DELPREV.is(e)) {
        editor.deletePrev();
        down = false;
      } else {
        consumed = false;
      }
    }
    if(consumed) e.consume();

    final byte[] tmp = editor.text();
    if(txt != tmp) {
      // text has changed: add old text to history
      hist.store(tmp, pos, editor.pos());
      scrollCode.invokeLater(down);
    } else if(pos != editor.pos() || selected != editor.selected()) {
      // cursor position or selection state has changed
      cursorCode.invokeLater(down ? 2 : 0);
    }
  }

  /** Updates the scroll bar. */
  private final GUICode scrollCode = new GUICode() {
    @Override
    public void execute(final Object down) {
      rend.updateScrollbar();
      cursorCode.execute((Boolean) down ? 2 : 0);
    }
  };

  /** Updates the cursor position. */
  private final GUICode cursorCode = new GUICode() {
    @Override
    public void execute(final Object algn) {
      // updates the visible area
      final int p = scroll.pos();
      final int y = rend.cursorY();
      final int m = y + rend.fontHeight() * 3 - getHeight();
      if(p < m || p > y) {
        final int align = (Integer) algn;
        scroll.pos(align == 0 ? y : align == 1 ? y - getHeight() / 2 : m);
        rend.repaint();
      }
    }
  };

  /** Last horizontal position. */
  private int lastCol = -1;

  @Override
  public void keyTyped(final KeyEvent e) {
    if(!hist.active() || control(e) || DELNEXT.is(e) || DELPREV.is(e) || ESCAPE.is(e)) return;

    final int caret = editor.pos();

    // remember if marked text is to be deleted
    final StringBuilder sb = new StringBuilder(1).append(e.getKeyChar());
    final boolean indent = TAB.is(e) && editor.indent(sb, e.isShiftDown());

    // delete marked text
    final boolean selected = editor.selected() && !indent;
    if(selected) editor.delete();

    final int move = ENTER.is(e) ? editor.enter(sb) : editor.add(sb, selected);

    // refresh history and adjust cursor position
    hist.store(editor.text(), caret, editor.pos());
    if(move != 0) editor.pos(Math.min(editor.size(), caret + move));

    // adjust text height
    scrollCode.invokeLater(true);
    e.consume();
  }

  /**
   * Releases a key or mouse. Can be overwritten to react on events.
   * @param action action
   */
  @SuppressWarnings("unused")
  protected void release(final Action action) { }

  // EDITOR COMMANDS ==========================================================

  /**
   * Copies the selected text to the clipboard.
   * @return true if text was copied
   */
  private boolean copy() {
    final String txt = editor.copy();
    if(txt.isEmpty()) return false;

    // copy selection to clipboard
    BaseXLayout.copy(txt);
    return true;
  }

  /**
   * Returns the clipboard text.
   * @return text
   */
  private static String clip() {
    // copy selection to clipboard
    final Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
    final Transferable tr = clip.getContents(null);
    if(tr != null) for(final Object o : BaseXLayout.contents(tr)) return o.toString();
    return null;
  }

  /**
   * Finishes a command.
   * @param old old cursor position; store entry to history if position != -1
   */
  private void finish(final int old) {
    if(old != -1) hist.store(editor.text(), old, editor.pos());
    scrollCode.invokeLater(true);
    release(Action.CHECK);
  }

  /** Text caret. */
  private final Timer caretTimer = new Timer(500, new ActionListener() {
    @Override
    public void actionPerformed(final ActionEvent e) {
      rend.caret(!rend.caret());
    }
  });

  /**
   * Stops an old text cursor thread and, if requested, starts a new one.
   * @param start start/stop flag
   */
  private void caret(final boolean start) {
    caretTimer.stop();
    if(start) caretTimer.start();
    rend.caret(start);
  }

  @Override
  public final void mouseWheelMoved(final MouseWheelEvent e) {
    scroll.pos(scroll.pos() + e.getUnitsToScroll() * 20);
    rend.repaint();
  }

  /** Calculation counter. */
  private final GUICode resizeCode = new GUICode() {
    @Override
    public void execute(final Object arg) {
      rend.updateScrollbar();
      // update scrollbar to display value within valid range
      scroll.pos(scroll.pos());
      rend.repaint();
    }
  };

  @Override
  public final void componentResized(final ComponentEvent e) {
    resizeCode.invokeLater();
  }

  /** Undo command. */
  class UndoCmd extends GUIPopupCmd {
    /** Constructor. */
    UndoCmd() { super(Text.UNDO, UNDOSTEP); }

    @Override
    public void execute() {
      if(!hist.active()) return;
      final byte[] t = hist.prev();
      if(t == null) return;
      editor.text(t);
      editor.pos(hist.caret());
      finish(-1);
    }
    @Override
    public boolean enabled(final GUI main) { return !hist.first(); }
  }

  /** Redo command. */
  class RedoCmd extends GUIPopupCmd {
    /** Constructor. */
    RedoCmd() { super(Text.REDO, REDOSTEP); }

    @Override
    public void execute() {
      if(!hist.active()) return;
      final byte[] t = hist.next();
      if(t == null) return;
      editor.text(t);
      editor.pos(hist.caret());
      finish(-1);
    }
    @Override
    public boolean enabled(final GUI main) { return !hist.last(); }
  }

  /** Cut command. */
  class CutCmd extends GUIPopupCmd {
    /** Constructor. */
    CutCmd() { super(Text.CUT, CUT1, CUT2); }

    @Override
    public void execute() {
      final int pos = editor.pos();
      if(!copy()) return;
      editor.delete();
      finish(pos);
    }
    @Override
    public boolean enabled(final GUI main) { return hist.active() && editor.selected(); }
  }

  /** Copy command. */
  class CopyCmd extends GUIPopupCmd {
    /** Constructor. */
    CopyCmd() { super(Text.COPY, COPY1, COPY2); }

    @Override
    public void execute() { copy(); }
    @Override
    public boolean enabled(final GUI main) { return editor.selected(); }
  }

  /** Paste command. */
  class PasteCmd extends GUIPopupCmd {
    /** Constructor. */
    PasteCmd() { super(Text.PASTE, PASTE1, PASTE2); }

    @Override
    public void execute() {
      final int pos = editor.pos();
      final String clip = clip();
      if(clip == null) return;
      if(editor.selected()) editor.delete();
      editor.add(clip);
      finish(pos);
    }
    @Override
    public boolean enabled(final GUI main) { return hist.active() && clip() != null; }
  }

  /** Delete command. */
  class DelCmd extends GUIPopupCmd {
    /** Constructor. */
    DelCmd() { super(Text.DELETE, DELNEXT); }

    @Override
    public void execute() {
      final int pos = editor.pos();
      editor.delete();
      finish(pos);
    }
    @Override
    public boolean enabled(final GUI main) { return hist.active() && editor.selected(); }
  }

  /** Select all command. */
  class AllCmd extends GUIPopupCmd {
    /** Constructor. */
    AllCmd() { super(Text.SELECT_ALL, SELECTALL); }

    @Override
    public void execute() { selectAll(); }
  }

  /** Find next hit. */
  class FindCmd extends GUIPopupCmd {
    /** Constructor. */
    FindCmd() { super(Text.FIND + Text.DOTS, FIND); }

    @Override
    public void execute() { search.activate(searchString(), true); }
    @Override
    public boolean enabled(final GUI main) { return search != null; }
  }

  /** Find next hit. */
  class FindNextCmd extends GUIPopupCmd {
    /** Constructor. */
    FindNextCmd() { super(Text.FIND_NEXT, FINDNEXT1, FINDNEXT2); }

    @Override
    public void execute() { find(true); }
    @Override
    public boolean enabled(final GUI main) { return search != null; }
  }

  /** Find previous hit. */
  class FindPrevCmd extends GUIPopupCmd {
    /** Constructor. */
    FindPrevCmd() { super(Text.FIND_PREVIOUS, FINDPREV1, FINDPREV2); }

    @Override
    public void execute() { find(false); }
    @Override
    public boolean enabled(final GUI main) { return search != null; }
  }

  /**
   * Highlights the next/previous hit.
   * @param next next/previous hit
   */
  private void find(final boolean next) {
    final boolean vis = search.isVisible();
    search.activate(searchString(), false);
    jump(vis ? next ? SearchDir.FORWARD : SearchDir.BACKWARD : SearchDir.CURRENT, true);
  }

  /** Go to line. */
  class GotoCmd extends GUIPopupCmd {
    /** Constructor. */
    GotoCmd() { super(Text.GO_TO_LINE + Text.DOTS, GOTOLINE); }

    @Override
    public void execute() { gotoLine(); }
    @Override
    public boolean enabled(final GUI main) { return search != null; }
  }

  /**
   * Jumps to a specific line.
   */
  private void gotoLine() {
    final byte[] last = editor.text();
    final int ll = last.length;
    final int cr = getCaret();
    int l = 1;
    for(int e = 0; e < ll && e < cr; e += cl(last, e)) {
      if(last[e] == '\n') ++l;
    }
    final DialogLine dl = new DialogLine(gui, l);
    if(!dl.ok()) return;
    final int el = dl.line();
    l = 1;
    int p = 0;
    for(int e = 0; e < ll && l < el; e += cl(last, e)) {
      if(last[e] != '\n') continue;
      p = e + 1;
      ++l;
    }
    setCaret(p);
    gui.editor.posCode.invokeLater();
  }
}
