package org.endeavour.enterprise.data;

import org.endeavour.enterprise.framework.database.DatabaseConnection;
import org.endeavour.enterprise.framework.database.StoredProcedure;
import org.endeavour.enterprise.model.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdministrationData
{
    public String getPasswordHash(String emailAddress) throws Exception
    {
        try (Connection connection = DatabaseConnection.get(DatabaseName.ENDEAVOUR_ENTERPRISE))
        {
            try (StoredProcedure storedProcedure = new StoredProcedure(connection, "Administration.GetPasswordHash"))
            {
                storedProcedure.setParameter("@EmailAddress", emailAddress);

                return (String)storedProcedure.executeScalar();
            }
        }
    }

    public User getUser(UUID userUuid)
    {
        return getUsers()
                .stream()
                .filter(t -> t.getUserUuid().equals(userUuid))
                .findFirst()
                .orElse(null);
    }

    public User getUser(String username)
    {
        return getUsers()
                .stream()
                .filter(t -> t.getEmail().equals(username))
                .findFirst()
                .orElse(null);
    }

    public List<User> getUsers()
    {
        List<User> users = new ArrayList<>();

        User user = new User();
        user.setUserUuid(UUID.fromString("b860db4c-7270-4e0f-a59f-77fa9b74973f"));
        user.setTitle("Dr");
        user.setForename("David");
        user.setSurname("Stables");
        user.setEmail("david.stables@endeavourhealth.org");

        UserInRole userInRole = new UserInRole();
        userInRole.setUserInRoleUuid(UUID.fromString("50ded6cd-17a1-4a05-9079-c176468ff90b"));
        userInRole.setOrganisationUuid(UUID.fromString("e9f71c8a-be36-42ff-8cd7-f2ab9f188a4f"));
        userInRole.setOrganisationName("Alpha Surgery");
        userInRole.setEndUserRole(EndUserRole.ADMIN);

        user.addUserInRole(userInRole);

        UserInRole userInRole2 = new UserInRole();
        userInRole2.setUserInRoleUuid(UUID.fromString("19d402a6-30f1-4175-acac-340d73a2ffb2"));
        userInRole2.setOrganisationUuid(UUID.fromString("31287afa-a5b9-4a0e-ac70-0646d0508adb"));
        userInRole2.setOrganisationName("Bravo Hospital");
        userInRole2.setEndUserRole(EndUserRole.USER);

        user.addUserInRole(userInRole2);

        user.setCurrentUserInRoleUuid(userInRole.getUserInRoleUuid());

        users.add(user);


        User user2 = new User();
        user2.setUserUuid(UUID.fromString("80EB4C43-0BB4-46E0-865B-0EE15481CA16"));
        user2.setTitle("Dr");
        user2.setForename("Kambiz");
        user2.setSurname("Boomla");
        user2.setEmail("kb");

        UserInRole userInRole3 = new UserInRole();
        userInRole3.setUserInRoleUuid(UUID.fromString("D2364591-B473-4CE7-89CB-5DB7BEA30536"));
        userInRole3.setOrganisationUuid(UUID.fromString("e9f71c8a-be36-42ff-8cd7-f2ab9f188a4f"));
        userInRole3.setOrganisationName("Alpha Surgery");
        userInRole3.setEndUserRole(EndUserRole.ADMIN);

        user2.addUserInRole(userInRole3);

        UserInRole userInRole4 = new UserInRole();
        userInRole4.setUserInRoleUuid(UUID.fromString("4E415BDE-FE23-4348-8435-E564F37C2A65"));
        userInRole4.setOrganisationUuid(UUID.fromString("31287afa-a5b9-4a0e-ac70-0646d0508adb"));
        userInRole4.setOrganisationName("Bravo Hospital");
        userInRole4.setEndUserRole(EndUserRole.USER);

        user2.addUserInRole(userInRole4);

        user2.setCurrentUserInRoleUuid(userInRole3.getUserInRoleUuid());

        users.add(user2);

        return users;
    }

    public List<Organisation> GetOrganisation() throws Exception
    {
        try (Connection conn = DatabaseConnection.get(DatabaseName.ENDEAVOUR_ENTERPRISE))
        {
            try (StoredProcedure storedProcedure = new StoredProcedure(conn, "Administration.GetOrganisation"))
            {
                storedProcedure.setParameter("@OrganisationId", 1);

                ResultSet resultSet = storedProcedure.executeQuery();

                ArrayList<Organisation> result = new ArrayList<>();

                while (resultSet.next())
                {
                    Organisation organisation = new Organisation();
                    organisation.setOrganisationUuid(UUID.fromString(resultSet.getString("OrganisationUuid")));
                    organisation.setName(resultSet.getString("Name"));

                    result.add(organisation);
                }

                return result;
            }
        }
    }
}
