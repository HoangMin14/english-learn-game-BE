package com.aws.alphabetuniverse.auth.service;

public interface CognitoUserAdminService {

    void disableUser(String cognitoSub);

    void enableUser(String cognitoSub);
}
