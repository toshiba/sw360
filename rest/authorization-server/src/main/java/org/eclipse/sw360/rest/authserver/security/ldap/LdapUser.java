package org.eclipse.sw360.rest.authserver.security.ldap;

import lombok.Data;

@Data
public class LdapUser {
    private String uid;
    private String sn;
    private String displayedName;
    private String givenName;
    private String name;
    private String mail;
}
