package org.openelisglobal.systemuser.service;

import java.util.List;
import org.openelisglobal.common.service.BaseObjectService;
import org.openelisglobal.systemuser.valueholder.SystemUser;

public interface SystemUserService extends BaseObjectService<SystemUser, String> {

    /**
     * Saves a user created by central login (Nidan KC-38). Identity is the Keycloak
     * sub in externalId, so the same-first-and-last-name rule for hand-entered
     * users does not apply: two staff may share a name.
     */
    SystemUser saveCentralLoginUser(SystemUser systemUser);

    void getData(SystemUser systemUser);

    List<SystemUser> getPageOfSystemUsers(int startingRecNo);

    List<SystemUser> getPagesOfSearchedUsers(int startRecNo, String searchString);

    List<SystemUser> getAllSystemUsers();

    Integer getTotalSystemUserCount();

    SystemUser getDataForLoginUser(String name);

    SystemUser getUserById(String userId);

    Integer getTotalSearchedUserCount(String searchString);
}
