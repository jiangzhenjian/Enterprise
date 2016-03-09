package org.endeavour.enterprise.entity.database;

import org.endeavour.enterprise.framework.configuration.Configuration;
import org.endeavour.enterprise.model.DatabaseName;
import org.endeavour.enterprise.model.DefinitionItemType;
import org.endeavour.enterprise.model.DependencyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.concurrent.Executor;

/**
 * Created by Drew on 29/02/2016.
 * Database implementation for SQL Server. To support other DB types, create a new sub-class of DatabaseI
 */
public final class SqlServerDatabase implements DatabaseI
{
    private static final Logger LOG = LoggerFactory.getLogger(SqlServerDatabase.class);
    private static final String ALIAS = "z";
    private static SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private LinkedList<PoolableConnection> connectionPool = new LinkedList<>();

    /**
     * converts objects to Strings for SQL, escaping as required
     */
    private static String convertToString(Object o)
    {
        if (o == null)
        {
            return "'null'";
        }
        else if (o instanceof String)
        {
            String s = ((String)o).replaceAll("'", "''");
            return "'" + s + "'";
        }
        else if (o instanceof Integer)
        {
            return "" + ((Integer)o).intValue();
        }
        else if (o instanceof UUID)
        {
            return "'" + ((UUID)o).toString() + "'";
        }
        else if (o instanceof Boolean)
        {
            if (((Boolean)o).booleanValue())
            {
                return "1";
            }
            else
            {
                return "0";
            }
        }
        else if (o instanceof Date)
        {
            return "'" + dateFormatter.format((Date)o) + "'";
        }
        else if (o instanceof DependencyType)
        {
            return "" + ((DependencyType)o).getValue();
        }
        else if (o instanceof DefinitionItemType)
        {
            return "" + ((DefinitionItemType)o).getValue();
        }
        else
        {
            LOG.error("Unsupported entity for database", o.getClass());
            return null;
        }
    }

    private int executeScalarCountQuery(String sql, String databaseName) throws Exception
    {
        Connection connection = getConnection(databaseName);
        Statement s = connection.createStatement();
        try
        {
            LOG.trace("Executing {}", sql);
            s.execute(sql);

            ResultSet rs = s.getResultSet();
            rs.next();
            int ret = rs.getInt(1);

            returnConnection(connection);

            return ret;
        }
        catch (SQLException sqlEx)
        {
            LOG.error("Error with SQL {}", sql);
            throw sqlEx;
        }
    }

    /**
     * 2016-03-02 DL - very basic connection pooling, as it's way too slow running against Azure when
     * we keep creating connections
     */
    private synchronized Connection getConnection(String databaseName) throws ClassNotFoundException, SQLException
    {
        try
        {
            while (true) {
                PoolableConnection connection = connectionPool.pop();

                //quick check on closed state, which could happen for old connections closed by network problems
                if (!connection.isClosed()) {
                    return connection;
                }
            }
        }
        catch (NoSuchElementException nsee)
        {}

        //if we get here, the pool didn't have one, so just create a new one
        Class.forName(net.sourceforge.jtds.jdbc.Driver.class.getCanonicalName());
        Connection conn = DriverManager.getConnection(Configuration.DB_CONNECTION_STRING);

        long expiry = System.currentTimeMillis() + Configuration.DB_CONNECTION_MAX_AGE_MILLIS;
        LOG.trace("Created new DB connection");

        return new PoolableConnection(conn, expiry, Configuration.DB_CONNECTION_LIVES);
    }
    private synchronized void returnConnection(Connection connection) throws SQLException
    {
        //if the connection has been closed discard it
        if (connection.isClosed()) {
            LOG.trace("Discarded returning DB connection as it's already closed");
            return;
        }

        //if the connection pool is already big enough or it's not a poolable connection, just close and discard
        if (connectionPool.size() >= Configuration.DB_CONNECTION_POOL_SIZE
                || !(connection instanceof PoolableConnection)) {
            LOG.trace("Discarded returning DB connection with pool size {} and connection class {}", connectionPool.size(), connection.getClass());
            connection.close();
            return;
        }

        //if the connection is too old, or has been used too many times, close and discard
        PoolableConnection poolable = (PoolableConnection)connection;
        int lives = poolable.getLives();
        long expiry = poolable.getExpiry();

        //decrement the lives
        lives --;
        poolable.setLives(lives);

        if (lives <= 0
                || expiry < System.currentTimeMillis())
        {
            LOG.trace("Discarded returning DB connection with lives {} and expiry {}", lives, expiry);
            connection.close();
            return;
        }

        //if we make it here, add to our pool
        connectionPool.push(poolable);
    }
    /*private Connection getConnection(String databaseName) throws ClassNotFoundException, SQLException
    {
        // databaseName not used at present

        Class.forName(net.sourceforge.jtds.jdbc.Driver.class.getCanonicalName());
        return DriverManager.getConnection(Configuration.DB_CONNECTION_STRING);
    }
    private void returnConnection(Connection connection) throws SQLException
    {
        if (!connection.isClosed())
        {
            connection.close();
        }
    }*/


    @Override
    public void writeUpdate(DbAbstractTable entity) throws Exception
    {
        TableAdapter a = entity.getAdapter();

        ArrayList<Object> values = new ArrayList<Object>();
        entity.writeForDb(values);

        String[] primaryKeyCols = a.getPrimaryKeyColumns();
        String[] cols = a.getColumns();

        List<String> nonKeyCols = new ArrayList<String>();
        HashMap<String, String> hmColValues = new HashMap<String, String>();

        for (int i=0; i<cols.length; i++)
        {
            String col = cols[i];
            Object value = values.get(i);
            String s = convertToString(value);

            hmColValues.put(col, s);

            //see if a primary key column
            boolean isPrimaryKey = false;
            for (int j=0; j<primaryKeyCols.length; j++)
            {
                String primaryKeyCol = primaryKeyCols[j];
                if (col.equalsIgnoreCase(primaryKeyCol))
                {
                    isPrimaryKey = true;
                    break;
                }
            }
            if (!isPrimaryKey)
            {
                nonKeyCols.add(col);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ");
        sb.append(a.getSchema());
        sb.append(".");
        sb.append(a.getTableName());
        sb.append(" SET ");

        for (int i=0; i<nonKeyCols.size(); i++)
        {
            String nonKeyCol = nonKeyCols.get(i);
            String val = hmColValues.get(nonKeyCol);

            if (i>0)
            {
                sb.append(", ");
            }

            sb.append(nonKeyCol);
            sb.append(" = ");
            sb.append(val);
        }

        sb.append(" WHERE ");

        for (int i=0; i<primaryKeyCols.length; i++)
        {
            String primaryKeyCol = primaryKeyCols[i];
            String val = hmColValues.get(primaryKeyCol);

            if (i>0)
            {
                sb.append("AND ");
            }

            sb.append(primaryKeyCol);
            sb.append(" = ");
            sb.append(val);
        }

        String sql = sb.toString();

        Connection connection = getConnection(a.getDatabase());
        Statement s = connection.createStatement();

        try
        {
            LOG.trace("Executing {}", sql);
            s.execute(sql);

            returnConnection(connection);
        }
        catch (SQLException sqlEx)
        {
            LOG.error("Error with SQL {}", sql);
            throw sqlEx;
        }
    }

    @Override
    public void writeInsert(DbAbstractTable entity) throws Exception
    {
        TableAdapter a = entity.getAdapter();

        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ");
        sb.append(a.getSchema());
        sb.append(".");
        sb.append(a.getTableName());
        sb.append(" VALUES (");

        ArrayList<Object> values = new ArrayList<Object>();
        entity.writeForDb(values);

        for (int i=0; i<values.size(); i++)
        {
            Object value = values.get(i);

            if (i>0)
            {
                sb.append(", ");
            }

            String s = convertToString(value);
            sb.append(s);
        }

        sb.append(")");

        String sql = sb.toString();

        Connection connection = getConnection(a.getDatabase());
        Statement s = connection.createStatement();

        try
        {
            LOG.trace("Executing {}", sql);
            s.execute(sql);

            returnConnection(connection);
        }
        catch (SQLException sqlEx)
        {
            LOG.error("Error with SQL {}", sql);
            throw sqlEx;
        }
    }

    @Override
    public void writeDelete(DbAbstractTable entity) throws Exception
    {
        TableAdapter a = entity.getAdapter();

        ArrayList<Object> values = new ArrayList<Object>();
        entity.writeForDb(values);

        String[] primaryKeyCols = a.getPrimaryKeyColumns();
        String[] cols = a.getColumns();

        HashMap<String, String> hmColValues = new HashMap<String, String>();

        for (int i=0; i<cols.length; i++)
        {
            String col = cols[i];
            Object value = values.get(i);
            String s = convertToString(value);

            hmColValues.put(col, s);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("DELETE FROM ");
        sb.append(a.getSchema());
        sb.append(".");
        sb.append(a.getTableName());
        sb.append(" WHERE ");

        for (int i=0; i<primaryKeyCols.length; i++)
        {
            String primaryKeyCol = primaryKeyCols[i];
            String val = hmColValues.get(primaryKeyCol);

            if (i>0)
            {
                sb.append("AND ");
            }

            sb.append(primaryKeyCol);
            sb.append(" = ");
            sb.append(val);
        }

        String sql = sb.toString();

        Connection connection = getConnection(a.getDatabase());
        Statement s = connection.createStatement();

        try
        {
            LOG.trace("Executing {}", sql);
            s.execute(sql);

            returnConnection(connection);
        }
        catch (SQLException sqlEx)
        {
            LOG.error("Error with SQL {}", sql);
            throw sqlEx;
        }

    }

    @Override
    public DbAbstractTable retrieveForPrimaryKeys(TableAdapter a, Object... keys) throws Exception
    {
        String[] primaryKeyCols = a.getPrimaryKeyColumns();
        if (primaryKeyCols.length != keys.length)
        {
            throw new RuntimeException("Primary keys length (" + primaryKeyCols.length + ")doesn't match keys length (" + keys.length + ")");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("WHERE ");

        for (int i=0; i<primaryKeyCols.length; i++)
        {
            String primaryKeyCol = primaryKeyCols[i];
            Object o = keys[i];
            String val = convertToString(o);

            if (i>0)
            {
                sb.append(" AND ");
            }

            sb.append(primaryKeyCol);
            sb.append(" = ");
            sb.append(val);
        }

        String whereStatement = sb.toString();
        return retrieveSingleForWhere(a, whereStatement);
    }
    private DbAbstractTable retrieveSingleForWhere(TableAdapter a, String whereStatement) throws Exception
    {
        List<DbAbstractTable> v = new ArrayList<DbAbstractTable>();
        retrieveForWhere(a, whereStatement, v);

        if (v.size() == 0)
        {
            return null;
        }
        else
        {
            return v.get(0);
        }
    }
    private void retrieveForWhere(TableAdapter a, String conditions, List ret) throws Exception
    {
        StringBuilder sb = new StringBuilder();

        sb.append("SELECT ");

        String[] cols = a.getColumns();
        for (int i=0; i<cols.length; i++)
        {
            String col = cols[i];

            if (i>0)
            {
                sb.append(", ");
            }

            sb.append(ALIAS);
            sb.append(".");
            sb.append(col);
        }

        sb.append(" FROM ");
        sb.append(a.getSchema());
        sb.append(".");
        sb.append(a.getTableName());
        sb.append(" ");
        sb.append(ALIAS);
        sb.append(" ");
        sb.append(conditions);

        String sql = sb.toString();

        Connection connection = getConnection(a.getDatabase());
        Statement s = connection.createStatement();
        try
        {
            LOG.trace("Executing {}", sql);
            s.execute(sql);

            ResultSet rs = s.getResultSet();

            ResultReader rr = new ResultReader(rs);

            while (rr.nextResult())
            {
                DbAbstractTable entity = a.newEntity();
                entity.readFromDb(rr);
                ret.add(entity);
            }

            returnConnection(connection);
        }
        catch (SQLException sqlEx)
        {
            LOG.error("Error with SQL {}", sql);
            throw sqlEx;
        }
    }

    @Override
    public DbEndUser retrieveEndUserForEmail(String email) throws Exception
    {
        String where = "WHERE Email = " + convertToString(email); //make sure to convert, to prevent SQL injection
        return (DbEndUser)retrieveSingleForWhere(new DbEndUser().getAdapter(), where);
    }

    @Override
    public List<DbEndUser> retrieveSuperUsers() throws Exception
    {
        List<DbEndUser> ret = new ArrayList<DbEndUser>();
        retrieveForWhere(new DbEndUser().getAdapter(), "WHERE IsSuperUser = 1", ret);
        return ret;
    }

    @Override
    public List<DbOrganisation> retrieveAllOrganisations() throws Exception
    {
        List<DbOrganisation> ret = new ArrayList<DbOrganisation>();
        retrieveForWhere(new DbOrganisation().getAdapter(), "WHERE 1=1", ret);
        return ret;
    }

    @Override
    public List<DbEndUserEmailInvite> retrieveEndUserEmailInviteForUserNotCompleted(UUID userUuid) throws Exception
    {
        List<DbEndUserEmailInvite> ret = new ArrayList<DbEndUserEmailInvite>();
        String where = "WHERE UserUuid = " + convertToString(userUuid)
                     + " AND DtCompleted > GETDATE()";
        retrieveForWhere(new DbEndUserEmailInvite().getAdapter(), where, ret);
        return ret;
    }

    @Override
    public DbEndUserEmailInvite retrieveEndUserEmailInviteForToken(String token) throws Exception
    {
        String where = "WHERE Token = " + convertToString(token); //make sure to convert, to prevent SQL injection
        return (DbEndUserEmailInvite)retrieveSingleForWhere(new DbEndUserEmailInvite().getAdapter(), where);
    }

    @Override
    public DbActiveItem retrieveActiveItemForItemUuid(UUID itemUuid) throws Exception
    {
        String where = "WHERE ItemUuid = " + convertToString(itemUuid);
        return (DbActiveItem)retrieveSingleForWhere(new DbActiveItem().getAdapter(), where);
    }

    @Override
    public DbEndUserPwd retrieveEndUserPwdForUserNotExpired(UUID endUserUuid) throws Exception
    {
        String where = "WHERE EndUserUuid = " + convertToString(endUserUuid)
                     + " AND DtExpired > GETDATE()";
        return (DbEndUserPwd)retrieveSingleForWhere(new DbEndUserPwd().getAdapter(), where);
    }

    @Override
    public List<DbOrganisationEndUserLink> retrieveOrganisationEndUserLinksForOrganisationNotExpired(UUID organisationUuid) throws Exception
    {
        List<DbOrganisationEndUserLink> ret = new ArrayList<DbOrganisationEndUserLink>();
        String where = "WHERE OrganisationUuid = " + convertToString(organisationUuid)
                     + " AND DtExpired > GETDATE()";
        retrieveForWhere(new DbOrganisationEndUserLink().getAdapter(), where, ret);
        return ret;
    }

    @Override
    public List<DbOrganisationEndUserLink> retrieveOrganisationEndUserLinksForUserNotExpired(UUID endUserUuid) throws Exception
    {
        List<DbOrganisationEndUserLink> ret = new ArrayList<DbOrganisationEndUserLink>();
        String where = "WHERE EndUserUuid = " + convertToString(endUserUuid)
                + " AND DtExpired > GETDATE()";
        retrieveForWhere(new DbOrganisationEndUserLink().getAdapter(), where, ret);
        return ret;
    }

    @Override
    public DbOrganisationEndUserLink retrieveOrganisationEndUserLinksForOrganisationEndUserNotExpired(UUID organisationUuid, UUID endUserUuid) throws Exception
    {
        String where = "WHERE OrganisationUuid = " + convertToString(organisationUuid)
                     + " AND EndUserUuid = " + convertToString(endUserUuid)
                     + " AND DtExpired > GETDATE()";
        return (DbOrganisationEndUserLink)retrieveSingleForWhere(new DbOrganisationEndUserLink().getAdapter(), where);
    }

    @Override
    public DbOrganisation retrieveOrganisationForNameNationalId(String name, String nationalId) throws Exception
    {
        String where = "WHERE Name = " + convertToString(name)
                + " AND NationalId = " + convertToString(nationalId);
        return (DbOrganisation)retrieveSingleForWhere(new DbOrganisation().getAdapter(), where);
    }

    @Override
    public List<DbItem> retrieveDependentItems(UUID organisationUuid, UUID itemUuid, DependencyType dependencyType) throws Exception
    {
        List<DbItem> ret = new ArrayList<DbItem>();

        String where = "INNER JOIN Definition.ActiveItemDependency d"
                     + " ON d.ItemUuid = " + convertToString(itemUuid)
                     + " AND d.DependencyTypeId = " + convertToString(dependencyType)
                     + " AND d.DependentItemUuid = " + ALIAS + ".ItemUuid"
                     + " INNER JOIN Definition.ActiveItem a"
                     + " ON a.ItemUuid = d.DependentItemUuid"
                     + " AND a.Version = " + ALIAS + ".version"
                     + " AND a.OrganisationUuid = " + convertToString(organisationUuid);

        retrieveForWhere(new DbItem().getAdapter(), where, ret);
        return ret;
    }

    @Override
    public List<DbItem> retrieveNonDependentItems(UUID organisationUuid, DependencyType dependencyType, DefinitionItemType itemType) throws Exception
    {
        List<DbItem> ret = new ArrayList<DbItem>();

        String where = "INNER JOIN Definition.ActiveItem a"
                     + " ON a.ItemUuid = " + ALIAS + ".ItemUuid"
                     + " AND a.Version = " + ALIAS + ".version"
                     + " AND a.ItemTypeId = " + convertToString(itemType)
                     + " AND a.OrganisationUuid = " + convertToString(organisationUuid)
                     + " WHERE NOT EXISTS ("
                     + "SELECT 1 FROM Definition.ActiveItemDependency d"
                     + " WHERE d.DependentItemUuid = " + ALIAS + ".ItemUuid"
                     + " AND d.DependencyTypeId = " + convertToString(dependencyType)
                     + ")";

        retrieveForWhere(new DbItem().getAdapter(), where, ret);
        return ret;
    }

    @Override
    public int retrieveCountDependencies(UUID itemUuid, DependencyType dependencyType) throws Exception
    {
        String sql = "SELECT COUNT(1)"
                + " FROM Definition.ActiveItemDependency"
                + " WHERE ItemUuid = " + convertToString(itemUuid)
                + " AND DependencyTypeId = " + convertToString(dependencyType);

        return executeScalarCountQuery(sql, DatabaseName.ENDEAVOUR_ENTERPRISE);
    }

    @Override
    public DbItem retrieveForUuidLatestVersion(UUID organisationUuid, UUID itemUuid) throws Exception
    {
        String where = "INNER JOIN Definition.ActiveItem a"
                + " ON a.ItemUuid = " + ALIAS + ".ItemUuid"
                + " AND a.Version = " + ALIAS + ".version"
                + " AND a.OrganisationUuid = " + convertToString(organisationUuid)
                + " WHERE " + ALIAS + ".ItemUuid = " + convertToString(itemUuid);
        return (DbItem)retrieveSingleForWhere(new DbItem().getAdapter(), where);
    }

    @Override
    public List<DbActiveItemDependency> retrieveActiveItemDependenciesForDependentItemType(UUID dependentItemUuid, DependencyType dependencyType) throws Exception
    {
        List<DbActiveItemDependency> ret = new ArrayList<DbActiveItemDependency>();

        String where = "WHERE DependentItemUuid = " + convertToString(dependentItemUuid)
                     + " AND DependencyTypeId = " + convertToString(dependencyType);
        retrieveForWhere(new DbActiveItemDependency().getAdapter(), where, ret);
        return ret;
    }

    @Override
    public List<DbActiveItem> retrieveActiveItemDependentItems(UUID organisationUuid, UUID itemUuid, DependencyType dependencyType) throws Exception
    {
        List<DbActiveItem> ret = new ArrayList<DbActiveItem>();

        String where = "INNER JOIN Definition.ActiveItemDependency d"
                + " ON d.ItemUuid = " + convertToString(itemUuid)
                + " AND d.DependencyTypeId = " + convertToString(dependencyType)
                + " AND d.DependentItemUuid = " + ALIAS + ".ItemUuid"
                + " WHERE " + ALIAS + ".OrganisationUuid = " + convertToString(organisationUuid);

        retrieveForWhere(new DbActiveItem().getAdapter(), where, ret);
        return ret;
    }

    @Override
    public List<DbActiveItemDependency> retrieveActiveItemDependenciesForItem(UUID itemUuid) throws Exception
    {
        List<DbActiveItemDependency> ret = new ArrayList<DbActiveItemDependency>();

        String where = "WHERE ItemUuid = " + convertToString(itemUuid);
        retrieveForWhere(new DbActiveItemDependency().getAdapter(), where, ret);
        return ret;
    }

    @Override
    public List<DbActiveItemDependency> retrieveActiveItemDependenciesForItemType(UUID itemUuid, DependencyType dependencyType) throws Exception
    {
        List<DbActiveItemDependency> ret = new ArrayList<DbActiveItemDependency>();

        String where = "WHERE ItemUuid = " + convertToString(itemUuid)
                     + " AND DependencyTypeId = " + convertToString(dependencyType);
        retrieveForWhere(new DbActiveItemDependency().getAdapter(), where, ret);
        return ret;
    }

    @Override
    public List<DbActiveItemDependency> retrieveActiveItemDependenciesForDependentItem(UUID dependentItemUuid) throws Exception
    {
        List<DbActiveItemDependency> ret = new ArrayList<DbActiveItemDependency>();

        String where = "WHERE DependentItemUuid = " + convertToString(dependentItemUuid);
        retrieveForWhere(new DbActiveItemDependency().getAdapter(), where, ret);
        return ret;
    }


}

class PoolableConnection implements Connection
{
    private Connection innerConnection = null;
    private long expiry = -1;
    private int lives = -1;

    public PoolableConnection(Connection connection, long expiry, int lives)
    {
        this.innerConnection = connection;
        this.expiry = expiry;
        this.lives = lives;
    }

    public long getExpiry() {
        return expiry;
    }

    public void setExpiry(long expiry) {
        this.expiry = expiry;
    }

    public int getLives() {
        return lives;
    }

    public void setLives(int lives) {
        this.lives = lives;
    }

    @Override
    public Statement createStatement() throws SQLException {
        return innerConnection.createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return innerConnection.prepareStatement(sql);
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        return innerConnection.prepareCall(sql);
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        return innerConnection.nativeSQL(sql);
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        innerConnection.setAutoCommit(autoCommit);
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return innerConnection.getAutoCommit();
    }

    @Override
    public void commit() throws SQLException {
        innerConnection.commit();
    }

    @Override
    public void rollback() throws SQLException {
        innerConnection.rollback();
    }

    @Override
    public void close() throws SQLException {
        innerConnection.close();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return innerConnection.isClosed();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return innerConnection.getMetaData();
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        innerConnection.setReadOnly(readOnly);
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return innerConnection.isReadOnly();
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        innerConnection.setCatalog(catalog);
    }

    @Override
    public String getCatalog() throws SQLException {
        return innerConnection.getCatalog();
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        innerConnection.setTransactionIsolation(level);
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return innerConnection.getTransactionIsolation();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return innerConnection.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        innerConnection.clearWarnings();
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return innerConnection.createStatement(resultSetType, resultSetConcurrency);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return innerConnection.prepareStatement(sql, resultSetType, resultSetConcurrency);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return innerConnection.prepareCall(sql, resultSetType, resultSetConcurrency);
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return innerConnection.getTypeMap();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        innerConnection.setTypeMap(map);
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        innerConnection.setHoldability(holdability);
    }

    @Override
    public int getHoldability() throws SQLException {
        return innerConnection.getHoldability();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        return innerConnection.setSavepoint();
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        return innerConnection.setSavepoint(name);
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        innerConnection.rollback(savepoint);
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        innerConnection.releaseSavepoint(savepoint);
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return innerConnection.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return innerConnection.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return innerConnection.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        return innerConnection.prepareStatement(sql, autoGeneratedKeys);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        return innerConnection.prepareStatement(sql, columnIndexes);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        return innerConnection.prepareStatement(sql, columnNames);
    }

    @Override
    public Clob createClob() throws SQLException {
        return innerConnection.createClob();
    }

    @Override
    public Blob createBlob() throws SQLException {
        return innerConnection.createBlob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        return innerConnection.createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return innerConnection.createSQLXML();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        return innerConnection.isValid(timeout);
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        innerConnection.setClientInfo(name, value);
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        innerConnection.setClientInfo(properties);
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        return innerConnection.getClientInfo(name);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return innerConnection.getClientInfo();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        return innerConnection.createArrayOf(typeName, elements);
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        return innerConnection.createStruct(typeName, attributes);
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        innerConnection.setSchema(schema);
    }

    @Override
    public String getSchema() throws SQLException {
        return innerConnection.getSchema();
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        innerConnection.abort(executor);
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        innerConnection.setNetworkTimeout(executor, milliseconds);
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return innerConnection.getNetworkTimeout();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return innerConnection.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return innerConnection.isWrapperFor(iface);
    }
}
