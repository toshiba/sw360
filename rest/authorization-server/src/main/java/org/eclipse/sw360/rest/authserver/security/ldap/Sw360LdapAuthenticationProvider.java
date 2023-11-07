package org.eclipse.sw360.rest.authserver.security.ldap;


import org.eclipse.sw360.rest.authserver.security.Sw360GrantedAuthority;
import org.eclipse.sw360.rest.authserver.security.Sw360UserDetailsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.filter.Filter;
import org.springframework.ldap.support.LdapUtils;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.List;

import static org.eclipse.sw360.rest.authserver.security.Sw360GrantedAuthority.READ;

public class Sw360LdapAuthenticationProvider implements AuthenticationProvider {
    @Autowired
    private LdapTemplate ldapTemplate;

    @Autowired
    private Sw360UserDetailsProvider sw360UserDetailsProvider;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        try {
            String userIdentifier = authentication.getName();
            Filter filter = new EqualsFilter("uid", userIdentifier);
            String possiblePassword = authentication.getCredentials().toString();

            boolean authenticate = ldapTemplate.authenticate(LdapUtils.emptyLdapName(), filter.encode(), possiblePassword);

            if (authenticate) {
                List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
                LdapUser userByUID = ldapTemplate.search(LdapUtils.emptyLdapName(), filter.encode(),
                        (AttributesMapper<LdapUser>) attributes -> {
                            LdapUser ldapUser = new LdapUser();
                            ldapUser.setUid(attributes.get("uid").toString());
                            ldapUser.setSn(attributes.get("sn").toString());
                            ldapUser.setMail(attributes.get("mail").toString());
                            ldapUser.setDisplayedName(attributes.get("displayName").toString());
                            ldapUser.setGivenName(attributes.get("givenName").toString());
                            return ldapUser;
                        }
                ).get(0);
                grantedAuthorities.add(new SimpleGrantedAuthority(READ.getAuthority()));
                mapLdapUserToSw360User(userByUID);
                UserDetails userDetails = new User(userByUID.getMail(), possiblePassword
                        , grantedAuthorities);
                return new UsernamePasswordAuthenticationToken(userDetails,
                        possiblePassword, grantedAuthorities);
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    public void mapLdapUserToSw360User(LdapUser ldapUser) {
        org.eclipse.sw360.datahandler.thrift.users.User user = sw360UserDetailsProvider.provideUserDetails(ldapUser.getMail(), null);
        if (user == null) {
            sw360UserDetailsProvider.createUserFromLdapUser(ldapUser);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}
