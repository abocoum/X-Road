/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.auth;

import lombok.extern.slf4j.Slf4j;
import org.jvnet.libpam.PAM;
import org.jvnet.libpam.PAMException;
import org.jvnet.libpam.UnixUser;
import org.niis.xroad.restapi.domain.Role;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niis.xroad.restapi.auth.AuthenticationIpWhitelist.KEY_MANAGEMENT_API_WHITELIST;

/**
 * PAM authentication provider.
 * Application has to be run as a user who has read access to /etc/shadow (
 * likely means that belongs to group shadow)
 * roles are granted with user groups, mappings in {@link Role}
 *
 * Authentication is limited with an IP whitelist.
 */
@Slf4j
@Configuration
@Profile("!devtools-test-auth")
public class PamAuthenticationProvider implements AuthenticationProvider {

    // from PAMLoginModule
    private static final String PAM_SERVICE_NAME = "xroad";

    public static final String KEY_MANAGEMENT_PAM_AUTHENTICATION = "keyManagementPam";
    public static final String FORM_LOGIN_PAM_AUTHENTICATION = "formLoginPam";
    // allow all ipv4 and ipv6
    private static final Iterable<String> FORM_LOGIN_IP_WHITELIST =
            Arrays.asList("::/0", "0.0.0.0/0");

    private final AuthenticationIpWhitelist authenticationIpWhitelist;
    private final GrantedAuthorityMapper grantedAuthorityMapper;

    /**
     * constructor
     * @param authenticationIpWhitelist whitelist that limits the authentication
     */
    public PamAuthenticationProvider(AuthenticationIpWhitelist authenticationIpWhitelist,
            GrantedAuthorityMapper grantedAuthorityMapper) {
        this.authenticationIpWhitelist = authenticationIpWhitelist;
        this.grantedAuthorityMapper = grantedAuthorityMapper;
    }

    /**
     * PAM authentication for form login, with corresponding IP whitelist
     * @return
     */
    @Bean(FORM_LOGIN_PAM_AUTHENTICATION)
    public PamAuthenticationProvider formLoginPamAuthentication() {
        AuthenticationIpWhitelist formLoginWhitelist = new AuthenticationIpWhitelist();
        formLoginWhitelist.setWhitelistEntries(FORM_LOGIN_IP_WHITELIST);
        return new PamAuthenticationProvider(formLoginWhitelist, grantedAuthorityMapper);
    }

    /**
     * PAM authentication for key management API, with corresponding IP whitelist
     * @return
     */
    @Bean(KEY_MANAGEMENT_PAM_AUTHENTICATION)
    public PamAuthenticationProvider keyManagementWhitelist(
            @Qualifier(KEY_MANAGEMENT_API_WHITELIST) AuthenticationIpWhitelist keyManagementWhitelist) {
        return new PamAuthenticationProvider(keyManagementWhitelist, grantedAuthorityMapper);
    }

    /**
     * users with these groups are allowed access
     */
    private static final Set<String> ALLOWED_GROUP_NAMES = Collections.unmodifiableSet(
            Arrays.stream(Role.values())
                .map(Role::getLinuxGroupName)
                .collect(Collectors.toSet()));

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        authenticationIpWhitelist.validateIpAddress(authentication);
        String username = String.valueOf(authentication.getPrincipal());
        String password = String.valueOf(authentication.getCredentials());
        PAM pam;
        try {
            pam = new PAM(PAM_SERVICE_NAME);
        } catch (PAMException e) {
            throw new AuthenticationServiceException("Could not initialize PAM.", e);
        }
        try {
            UnixUser user = pam.authenticate(username, password);
            Set<String> groups = user.getGroups();
            Set<String> matchingGroups = groups.stream()
                    .filter(ALLOWED_GROUP_NAMES::contains)
                    .collect(Collectors.toSet());
            if (matchingGroups.isEmpty()) {
                throw new AuthenticationServiceException("user hasn't got any required groups");
            }
            Collection<Role> xroadRoles = matchingGroups.stream()
                    .map(groupName -> Role.getForGroupName(groupName).get())
                    .collect(Collectors.toSet());
            Set<GrantedAuthority> grants = grantedAuthorityMapper.getAuthorities(xroadRoles);
            return new UsernamePasswordAuthenticationToken(user.getUserName(), authentication.getCredentials(), grants);
        } catch (PAMException e) {
            throw new BadCredentialsException("PAM authentication failed.", e);
        } finally {
            pam.dispose();
        }
    }
    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(
                UsernamePasswordAuthenticationToken.class);
    }
}

