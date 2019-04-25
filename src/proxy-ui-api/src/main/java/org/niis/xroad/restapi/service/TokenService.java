/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.repository.TokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * client service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("denyAll")
public class TokenService {

    @Autowired
    private TokenRepository tokenRepository;

    /**
     * get all tokens
     * @return
     * @throws Exception
     */
    public List<TokenInfo> getAllTokens() throws Exception {
        return tokenRepository.getTokens();
    }

    /**
     * get all certificates for a given client.
     *
     * @param clientType client who's member certificates need to be
     *                   linked to
     * @return
     * @throws Exception
     */
    @PreAuthorize("hasAuthority('VIEW_CLIENT_DETAILS')")
    public List<CertificateInfo> getAllTokens(ClientType clientType) throws Exception {
        List<TokenInfo> tokenInfos = getAllTokens();
        return tokenInfos.stream()
                .flatMap(tokenInfo -> tokenInfo.getKeyInfo().stream())
                .flatMap(keyInfo -> keyInfo.getCerts().stream())
                .filter(certificateInfo -> clientType.getIdentifier().memberEquals(certificateInfo.getMemberId()))
                .collect(toList());
    }

}