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
package org.niis.xroad.restapi.service;

import lombok.Setter;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verify internal and external configurations
 */
@Component
public class ConfigurationVerifier {
    public static final int EXIT_STATUS_ANCHOR_NOT_FOR_EXTERNAL_SOURCE = 120;
    public static final int EXIT_STATUS_MISSING_PRIVATE_PARAMS = 121;
    public static final int EXIT_STATUS_UNREACHABLE = 122;
    public static final int EXIT_STATUS_OUTDATED = 123;
    public static final int EXIT_STATUS_INVALID_SIGNATURE = 124;
    public static final int EXIT_STATUS_OTHER = 125;

    public static final String ANCHOR_NOT_FOR_EXTERNAL_SOURCE = "conf_verification.anchor_not_for_external_source";
    public static final String MISSING_PRIVATE_PARAMS = "conf_verification.missing_private_params";
    public static final String UNREACHABLE = "conf_verification.unreachable";
    public static final String OUTDATED = "conf_verification.outdated";
    public static final String SIGNATURE = "conf_verification.signature_invalid";
    public static final String OTHER = "conf_verification.other";

    @Setter
    private String internalConfVerificationScriptPath;

    private final ExternalProcessRunner externalProcessRunner;

    @Autowired
    public ConfigurationVerifier(ExternalProcessRunner externalProcessRunner,
            @Value("${script.internal-configuration-verifier.path}") String internalConfVerificationScriptPath) {
        this.externalProcessRunner = externalProcessRunner;
        this.internalConfVerificationScriptPath = internalConfVerificationScriptPath;
    }

    /**
     * Verify internal configuration anchor.
     * @param anchorPath path to the configuration anchor to be verified
     * @throws ProcessNotExecutableException
     * @throws ProcessFailedException
     * @throws InterruptedException if the thread running the verifier is interrupted. <b>The interrupted thread has
     * already been handled with so you can choose to ignore this exception if you so please.</b>
     * @throws ConfigurationVerificationException when a known exception happens during verification. An error code
     * is attached to this exception according to the correct exit code
     */
    public void verifyInternalConfiguration(String anchorPath) throws ProcessNotExecutableException,
            ProcessFailedException, InterruptedException, ConfigurationVerificationException {
        ExternalProcessRunner.ProcessResult processResult =
                externalProcessRunner.execute(internalConfVerificationScriptPath, anchorPath);
        int exitCode = processResult.getExitCode();
        switch (exitCode) {
            case 0:
                break;
            case EXIT_STATUS_ANCHOR_NOT_FOR_EXTERNAL_SOURCE:
                throw new ConfigurationVerificationException(ANCHOR_NOT_FOR_EXTERNAL_SOURCE);
            case EXIT_STATUS_MISSING_PRIVATE_PARAMS:
                throw new ConfigurationVerificationException(MISSING_PRIVATE_PARAMS);
            case EXIT_STATUS_UNREACHABLE:
                throw new ConfigurationVerificationException(UNREACHABLE);
            case EXIT_STATUS_OUTDATED:
                throw new ConfigurationVerificationException(OUTDATED);
            case EXIT_STATUS_INVALID_SIGNATURE:
                throw new ConfigurationVerificationException(SIGNATURE);
            case EXIT_STATUS_OTHER:
                throw new ConfigurationVerificationException(OTHER);
            default:
                throw new RuntimeException("Internal configuration verifier exited with an unknown code: " + exitCode);
        }
    }

    /**
     * Thrown when the configuration verification script fails
     */
    public static class ConfigurationVerificationException extends ServiceException {
        /**
         * @param errorCode i18n key for an error code string
         */
        public ConfigurationVerificationException(String errorCode) {
            super("Configuration verification failed: " + errorCode, new ErrorDeviation(errorCode));
        }
    }
}
