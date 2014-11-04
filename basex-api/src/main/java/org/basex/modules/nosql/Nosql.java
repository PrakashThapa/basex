package org.basex.modules.nosql;

import static org.basex.util.Token.*;

import org.basex.build.*;
import org.basex.io.parse.json.JsonConverter;
import org.basex.io.serial.SerialMethod;
import org.basex.io.serial.SerializerOptions;
import org.basex.modules.nosql.NosqlOptions.NosqlFormat;
import org.basex.query.QueryException;
import org.basex.query.QueryModule;
import org.basex.query.expr.*;
import org.basex.query.func.*;
import org.basex.query.func.json.*;
import org.basex.query.value.item.*;
import org.basex.query.value.type.*;

/**
 * All Nosql database common functionality.
 *
 * @author BaseX Team 2005-13, BSD License
 * @author Prakash Thapa
 */
abstract class Nosql extends QueryModule {
  /* for couchbase. **/
  /** descending. */
  protected static final String DESCENDING = "descending";
  /** endkey. */
  protected static final String ENDKEY = "endkey";
  /** group. */
  protected static final String GROUP = "group";
  /** group_level. */
  protected static final String GROUP_LEVEL = "group_level";
  /** key. */
  protected static final String KEY = "key";
  /** keys. */
  protected static final String KEYS = "keys";
  /** limit. */
  protected static final String LIMIT = "limit";
  /** reduce. */
  protected static final String REDUCE = "reduce";
  /** skip. */
  protected static final String SKIP = "skip";
  /** stale. */
  protected static final String STALE = "stale";
  /** startkey. */
  protected static final String STARTKEY = "startkey";
  /** debug. */
  protected static final String DEBUG = "debug";
  /** viewmode. */
  protected static final String VIEWMODE = "viewmode";
  /** ok. */
  protected static final String OK = "ok";
  /** false. */
  protected static final String FALSE = "false";
  /** update_after. */
  protected static final String UPDATE_AFTER = "update_after";
  /** range. */
  protected static final String RANGE = "range";
  /** valueonly. */
  protected static final String VALUEONLY = "valueonly";
  /** includedocs. */
  protected static final String INCLUDEDOCS = "includedocs";
  /** descending. */
  /** for mongodb **/
  /** sort. */
  protected static final String SORT = "sort";
  /** count. */
  protected static final String COUNT = "count";
  /** explain. */
  protected static final String EXPLAIN = "explain";
  /** map. */
  protected static final String MAP = "map";
  /** query. */
  protected static final String QUERY = "query";
  /** finalalize. */
  protected static final String FINALIZE = "finalalize";
  /** fields findAndModify. */
  protected static final String FIELDS = "fields";
  /** update findAndModify. */
  protected static final String UPDATE = "update";
  /** returnnew findAndModify. */
  protected static final String NEW = "new";
  /** outputs. */
  protected static final String OUTPUTS = "outputs";
  /** outputtype. */
  protected static final String OUTPUTTYPE = "outputtype";
  /** upsert. */
  protected static final String UPSERT = "upsert";
  /** for rethink **/
  /** id. */
  protected static final String ID = "id";


  /** NOSQL URI. added later */
  protected static final byte[] NOSQLURI = token("http://www.basex.org/modules/nosql");
  /** Type type. */
  protected static final byte[] TYPE = token("type");
  /** Type object. */
  protected static final byte[] OBJECT = token("object");
  /** Type array. */
  protected static final byte[] ARRAY = token("array");
  /** Type int. */
  protected static final byte[] INT = AtomType.INT.string();
  /** Type string. */
  protected static final byte[] STRING = AtomType.STR.string();
  /** Type boolean. */
  protected static final byte[] BOOL = AtomType.BLN.string();
  /** Type date. */
  protected static final byte[] DATE = AtomType.DAT.string();
  /** Type double. */
  protected static final byte[] DOUBLE = AtomType.DBL.string();
  /** Type float. */
  protected static final byte[] FLOAT = AtomType.FLT.string();
  /** Type short. */
  protected static final byte[] SHORT = AtomType.SHR.string();
  /** Type time. */
  protected static final byte[] TIME = AtomType.TIM.string();
  /** Type timestamp. */
  protected static final byte[] TIMESTAMP = token("timestamp");

  /** Name. */
  protected static final String NAME = "name";
  /** qnmOptions. */
    /**
     * convert Str to java string.
     * @param item Str.
     * @return String
     */
    protected String itemToString(final Item item) {
      return ((Str) item).toJava();
    }
    /**
     * check json string and if valid return java string as result.
     * @param json json string
     * @return Item
     * @throws QueryException query exception
     */
    protected String itemToJsonString(final Item json) throws QueryException {
        if(json instanceof Str) {
            try {
                checkJson(json);
                String string = ((Str) json).toJava();
                return string;
            } catch (Exception e) {
                throw new QueryException("Item is not in well format");
            }
        }
        throw NosqlErrors.generalExceptionError("Item is not in xs:string format");
    }
    /**
     * string format for json.
     * @param json json string
     * @return Item formated json
     * @throws QueryException query exception
     */
    protected Item formatjson(final Str json)
            throws QueryException {
        final SerializerOptions sopts = new SerializerOptions();
        sopts.set(SerializerOptions.METHOD, SerialMethod.JSON);
        return jsonItem(json, true);
    }
    /**
     *check if Item is valid json not.
     * @param doc Item (string)
     * @return boolean
     * @throws QueryException exception
     */
    protected boolean checkJson(final Item doc) throws QueryException {
        try {
            jsonItem(doc, false);
            return true;
        } catch (Exception e) {
            throw NosqlErrors.jsonFormatError();
        }
    }
    /**
     * Convert json string to appropriate result using NosqlOption's type.
     * @param json json Str
     * @param opt Nosql Options
     * @return Item
     * @throws Exception exception
     */
    protected Item finalResult(final Str json, final NosqlOptions opt)
            throws Exception {
            try {
              if(opt != null) {
                  if(opt.get(NosqlOptions.TYPE) == NosqlFormat.XML) {
                    final JsonConverter conv = JsonConverter.get(jsonParseOption(opt));
                    conv.convert(json.string(), null);
                    return conv.finish();
                  }
                  Item xXml = jsonItem(json, false);
                  return jsonItem(xXml, true);
              }
              return jsonItem(json, false);
            } catch (final Exception ex) {
                throw new QueryException(ex);
            }
    }
    /** json to Item.
     * @param json item
     * @param isSerialize boolean
     * @return item.
     * @throws QueryException exception
     */
    protected Item jsonItem(final Item json, final boolean isSerialize) throws QueryException {
      Expr[] ex = {json};
      if(isSerialize)
        return new JsonSerialize().
            init(staticContext, null, Function._JSON_SERIALIZE, ex).
            item(queryContext, null);
      return new JsonParse().init(staticContext, null, Function._JSON_PARSE, ex).
      item(queryContext, null);


    }
    /** convert Nosql Options to jsonParseOptions.
     * @param opt NosqlOptions
     * @return JsonParserOptions
     */
    private JsonParserOptions jsonParseOption(final NosqlOptions opt) {
      final JsonParserOptions opts = new JsonParserOptions();
      opts.set(JsonOptions.FORMAT, opt.get(JsonOptions.FORMAT));
      opts.set(JsonOptions.STRINGS, opt.get(JsonOptions.STRINGS));
      opts.set(JsonOptions.LAX, opt.get(JsonOptions.LAX));
      opts.set(JsonOptions.MERGE, opt.get(JsonOptions.MERGE));
      return opts;
    }
   /**
    * check all special characters in string for valid json key.
    * @param string String value to be checked
    * @return String
    */
   protected String quote(final String string) {
     if (string == null || string.length() == 0) {
         return "\"\"";
     }

     char         c = 0;
     int          i;
     int          len = string.length();
     StringBuilder sb = new StringBuilder(len + 4);
     String       t;

     sb.append('"');
     for (i = 0; i < len; i += 1) {
         c = string.charAt(i);
         switch (c) {
         case '\\':
         case '"':
             sb.append('\\');
             sb.append(c);
             break;
         case '/':
//               if (b == '<') {
                 sb.append('\\');
//               }
             sb.append(c);
             break;
         case '\b':
             sb.append("\\b");
             break;
         case '\t':
             sb.append("\\t");
             break;
         case '\n':
             sb.append("\\n");
             break;
         case '\f':
             sb.append("\\f");
             break;
         case '\r':
            sb.append("\\r");
            break;
         default:
             if (c < ' ') {
                 t = "000" + Integer.toHexString(c);
                 sb.append("\\u" + t.substring(t.length() - 4));
             } else {
                 sb.append(c);
             }
         }
     }
     sb.append('"');
     return sb.toString();
 }
}
