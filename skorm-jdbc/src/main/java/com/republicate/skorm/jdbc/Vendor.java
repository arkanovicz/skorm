package com.republicate.skorm.jdbc;

import com.republicate.skorm.MetaInfos;
import com.republicate.skorm.config.ConfigDigester;
import com.republicate.skorm.config.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public class Vendor implements MetaInfos
{
    private static Map<String, Vendor> vendorMap = new HashMap<>();

    protected static Logger logger = LoggerFactory.getLogger("jdbc");

    public static Vendor getVendor(DatabaseMetaData meta) throws SQLException
    {
        Vendor vendor = null;
        String url = meta.getURL();
        int colon;
        if (url == null ||
            !url.startsWith("jdbc:") ||
            (colon = url.indexOf(':', 5)) == -1) throw new SQLException("invalid JDBC url: " + url);
        String vendorTag = url.substring(5, colon);

        if (!vendorMap.containsKey(vendorTag))
        {
            synchronized (vendorMap)
            {
                if (!vendorMap.containsKey(vendorTag))
                {
                    vendorMap.put(vendorTag, initalizeVendor(vendorTag, meta));
                }
            }
        }
        return vendorMap.get(vendorTag);
    }

    private static Vendor initalizeVendor(String vendorTag, DatabaseMetaData meta) throws SQLException
    {
        Vendor vendor = new Vendor();
        boolean configured = false;
        InputStream stream = null;
        try
        {
            String propertiesFile = "/" + vendorTag + ".driver.properties";
            stream = Vendor.class.getResourceAsStream(propertiesFile);
            if (stream == null)
            {
                logger.info("vendor properties file {} not found", propertiesFile);
            }
            else
            {
                final Properties properties = new Properties();
                properties.load(stream);
                ConfigDigester.setProperties(vendor, properties);
                configured = true;
            }
        }
        catch (IOException ioe)
        {
            logger.error("could not initlialize jdbc vendor properties", ioe);
        }
        finally
        {
            if (stream != null)
            {
                try
                {
                    stream.close();
                }
                catch (IOException ioe) {}
            }
        }
        if (!configured)
        {
            logger.info("falling back to discovery mode");
            vendor.setTag(vendorTag);
            vendor.configureUsingMetaData(meta);
        }
        return vendor;
    }

    private void configureUsingMetaData(DatabaseMetaData meta) throws SQLException
    {
        // case sensivity
        if (meta.storesLowerCaseIdentifiers()) tablesCaseSensitivity = CaseSensitivity.LOWERCASE;
        else if (meta.storesUpperCaseIdentifiers()) tablesCaseSensitivity = CaseSensitivity.UPPERCASE;
        else if (meta.supportsMixedCaseIdentifiers()) tablesCaseSensitivity = CaseSensitivity.SENSITIVE;

        // last insert id policy
        if (meta.supportsGetGeneratedKeys() && meta.generatedKeyAlwaysReturned()) lastInsertIdPolicy = LastInsertIdPolicy.GENERATED_KEYS;

        // identifier quote char
        String quoteString = meta.getIdentifierQuoteString();
        if (quoteString.length() == 1) identifierQuoteChar = quoteString.charAt(0);
    }

    /** jdbc tag of the database vendor */
    private String tag = "unknown";

    private String catalog = null;

    /** ping SQL query */
    private String pingQuery = null;

    /** case-sensivity */
    public enum CaseSensitivity {
        UNKNOWN('X'),
        SENSITIVE('S'),
        LOWERCASE('L'),
        UPPERCASE('U');
        public char code;
        CaseSensitivity(char code) { this.code = code; }
    }
    private CaseSensitivity tablesCaseSensitivity = null;
    private UnaryOperator<String> filterTableName = t -> t;

    @Override
    public char getIdentifierInternalCase() {
        return tablesCaseSensitivity.code;
    }

    /** SQL query to set the current schema */
    private String setSchemaQuery = null;

    /** ID generation method */
    public enum LastInsertIdPolicy { NONE, GENERATED_KEYS, RETURNING, QUERY, METHOD }
    private LastInsertIdPolicy lastInsertIdPolicy = LastInsertIdPolicy.NONE;
    private String lastInsertIdQuery = null;
    private String lastInsertIdMethod = null;

    /** whether the JDBC driver is strict about column types */
    private boolean strictColumnTypes = true;

    /** ignore tables matching this pattern */
    private Pattern ignoreTablesPattern = null;

    /** quoteIdentifier quote character */
    private Character identifierQuoteChar = null;

    /** whether driver supports ::varchar etc... */
    private boolean columnMarkers = false;

    /** sql query to get enum values */
    private String describeEnumQuery = null;
    private String describeEnumPattern = null;

    public String getTag()
    {
        return tag;
    }

    public void setTag(String tag)
    {
        this.tag = tag;
    }

    public String getPingQuery()
    {
        return pingQuery;
    }

    public void setPingQuery(String query)
    {
        this.pingQuery = pingQuery;
    }

    public CaseSensitivity getTablesCaseSensitivity()
    {
        return tablesCaseSensitivity;
    }

    public void setTablesCaseSensitivity(CaseSensitivity tablesCaseSensitivity)
    {
        this.tablesCaseSensitivity = tablesCaseSensitivity;
        switch (tablesCaseSensitivity)
        {
            case LOWERCASE: filterTableName = t -> t.toLowerCase(Locale.ROOT); break;
            case UPPERCASE: filterTableName = t -> t.toUpperCase(Locale.ROOT); break;
            default: filterTableName = t -> t;
        }
    }

    public String getSetSchemaQuery()
    {
        return setSchemaQuery;
    }

    public void setSetSchemaQuery(String setSchemaQuery)
    {
        this.setSchemaQuery = setSchemaQuery;
    }

    public LastInsertIdPolicy getLastInsertIdPolicy()
    {
        return lastInsertIdPolicy;
    }

    public String getLastInsertIdPolicyString()
    {
        if (lastInsertIdPolicy == null)
        {
            return null;
        }
        switch (lastInsertIdPolicy)
        {
            case NONE: return "none";
            case GENERATED_KEYS: return "generated_keys";
            case METHOD: return "method:" + getLastInsertIdMethod();
            case QUERY: return "query:" + getLastInsertIdQuery();
            default: return null;
        }
    }

    static Pattern classMethodPattern = Pattern.compile("((?:\\w+\\.)*\\w+\\.\\w)(?:\\(\\))?");

    public void setLastInsertIdPolicy(String policy)
    {
        policy = policy.trim();
        if (policy.startsWith("query:"))
        {
            lastInsertIdPolicy = LastInsertIdPolicy.QUERY;
            lastInsertIdQuery = policy.substring(6).trim();
        }
        else if (policy.startsWith("method:"))
        {
            lastInsertIdPolicy = LastInsertIdPolicy.METHOD;
            lastInsertIdMethod = policy.substring(7).trim();
        }
        else
        {
            lastInsertIdPolicy = LastInsertIdPolicy.valueOf(policy.toUpperCase());
            if (lastInsertIdPolicy == LastInsertIdPolicy.RETURNING)
            {
                throw new ConfigurationException("'returning' last insert id policy is not yet supported");
            }
        }
    }

    public String getLastInsertIdQuery()
    {
        return lastInsertIdQuery;
    }

    public String getLastInsertIdMethod()
    {
        return lastInsertIdMethod;
    }

    @Override
    public boolean isStrictColumnTypes()
    {
        return strictColumnTypes;
    }

    public void setStrictColumnTypes(boolean strictColumnTypes)
    {
        this.strictColumnTypes = strictColumnTypes;
    }

    public Pattern getIgnoreTablesPattern()
    {
        return ignoreTablesPattern;
    }

    public void setIgnoreTablesPattern(String pattern)
    {
        if (pattern != null && pattern.length() > 0)
        {
            ignoreTablesPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        }
        else
        {
            ignoreTablesPattern = null;
        }
    }

    @Override
    public char getIdentifierQuoteChar()
    {
        return identifierQuoteChar;
    }

    public void setIdentifierQuoteChar(String identifierQuoteChar)
    {
        identifierQuoteChar = identifierQuoteChar.trim();
        if (identifierQuoteChar.length() > 0)
        {
            this.identifierQuoteChar = identifierQuoteChar.charAt(0);
        }
    }

    @Override
    public boolean hasColumnMarkers()
    {
        return columnMarkers;
    }

    public void setColumnMarkers(boolean columnMarkers)
    {
        this.columnMarkers = columnMarkers;
    }

    public String getTableName(String entityName)
    {
        return filterTableName.apply(entityName);
    }

    public String quoteIdentifier(String id)
    {
        if (identifierQuoteChar == ' ')
        {
            return id;
        }
        else
        {
            return identifierQuoteChar + id + identifierQuoteChar;
        }
    }

    public String getDescribeEnumQuery()
    {
        return describeEnumQuery;
    }

    public String getDescribeEnumPattern()
    {
        return describeEnumPattern;
    }

    public void setDescribeEnum(String describeEnum)
    {
        int sep = describeEnum.indexOf('|');
        if (sep == -1)
        {
            describeEnumQuery = describeEnum;
            describeEnumPattern = null;
        }
        else
        {
            describeEnumQuery = describeEnum.substring(0, sep);
            describeEnumPattern = describeEnum.substring(sep + 1);
        }
    }

    /**
     * Check whether to ignore or not this table.
     *
     * @param name table name
     * @return whether to ignore this table
     */
    public boolean ignoreTable(String name)
    {
        return ignoreTablesPattern != null && ignoreTablesPattern.matcher(name).matches();
    }

    /**
     * Driver-specific value filtering
     *
     * @param value value to be filtered
     * @return filtered value
     */
    public Object filterValue(Object value)
    {
        if(value instanceof Calendar && "mysql".equals(tag))
        {
            value = ((Calendar)value).getTime();
        }
        return value;
    }

}
