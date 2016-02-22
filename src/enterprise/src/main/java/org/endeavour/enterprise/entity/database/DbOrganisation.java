package org.endeavour.enterprise.entity.database;

import org.endeavour.enterprise.model.DatabaseName;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Created by Drew on 17/02/2016.
 */
public final class DbOrganisation extends DbAbstractTable {

    private String name = null;
    private String nationalId = null;

    //register as a DB entity
    private static TableAdapter adapter = new TableAdapter(DbOrganisation.class,
                                                "Organisation", "Administration", DatabaseName.ENDEAVOUR_ENTERPRISE);


    public DbOrganisation()
    {}

    @Override
    public TableAdapter getAdapter() {
        return adapter;
    }

    @Override
    public void writeForDb(InsertBuilder builder)
    {
        builder.add(getPrimaryUuid());
        builder.add(name);
        builder.add(nationalId);
    }

    @Override
    public void readFromDb(ResultReader reader) throws SQLException
    {
        setPrimaryUuid(reader.readUuid());
        name = reader.readString();
        nationalId = reader.readString();
    }

    public static List<DbAbstractTable> retrieveForAll() throws Throwable
    {
        return adapter.retrieveEntities("Administration.Organisation_SelectForAll");
    }
    public static DbOrganisation retrieveForUuid(UUID uuid) throws Throwable
    {
        return (DbOrganisation)adapter.retrieveSingleEntity("Administration.Organisation_SelectForUuid", uuid);
    }

    /**
     * gets/sets
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNationalId() {
        return nationalId;
    }

    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
    }
}