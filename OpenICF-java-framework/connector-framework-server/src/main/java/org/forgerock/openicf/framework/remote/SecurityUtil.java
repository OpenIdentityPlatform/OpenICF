/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015-2016 ForgeRock AS. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

package org.forgerock.openicf.framework.remote;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.agreement.srp.SRP6Client;
import org.bouncycastle.crypto.agreement.srp.SRP6Server;
import org.bouncycastle.crypto.agreement.srp.SRP6VerifierGenerator;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.identityconnectors.common.Pair;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

import javax.crypto.KeyAgreement;

/**
 * @since 1.5
 */
public class SecurityUtil {

    private static final Log logger = Log.getLog(SecurityUtil.class);

    /**
     * Create a public key from encoded byte array.
     *
     * @param publicKeyBytes encoded X509 key specification
     * @return public key from byte array
     */
    public static PublicKey createPublicKey(byte[] publicKeyBytes) {
        try {

            KeyFactory kf = KeyFactory.getInstance("ECDSA");
            return kf.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to load ECDSA key factory", e);
            throw new ConnectorException(e.getMessage(), e);
        } catch (InvalidKeySpecException e) {
            logger.error("Failed to load public key from bytes", e);
            throw new ConnectorException(e.getMessage(), e);
        }
    }

    /**
     * Generating an EC key pair using the P-521 curve.
     *
     * @return new generated KeyPair
     */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            ECGenParameterSpec ecsp = new ECGenParameterSpec("secp521r1");
            keyGen.initialize(ecsp, new SecureRandom());
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to load EC key generator", e);
            throw new ConnectorException(e.getMessage(), e);
        } catch (InvalidAlgorithmParameterException e) {
            logger.error("Failed to load EC Curve P-521", e);
            throw new ConnectorException(e.getMessage(), e);
        }
    }

    /**
     * Do an Diffie–Hellman key exchange with EC keys.
     *
     * @param keyPrivate private EC key
     * @param dataPub    public EC key
     * @return secrete byte[66] array
     */
    public static byte[] doECDH(KeyPair keyPrivate, PublicKey dataPub) {
        try {
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(keyPrivate.getPrivate());
            ka.doPhase(dataPub, true);
            return ka.generateSecret();
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to load ECDH agreement", e);
            throw new ConnectorException(e.getMessage(), e);
        } catch (InvalidKeyException e) {
            logger.error("Failed to load ECDH key", e);
            throw new ConnectorException(e.getMessage(), e);
        }
    }


    /**
     * Generate Secure Remote Password (SRP)
     *
     * @param username user name (aka "identity")
     * @param password password
     * @param random   the source of randomness for this generator
     * @param params   group parameters (prime, generator)
     * @return generated verifier and random
     */
    public static Pair<String, byte[]> generateVerifier(String username, String password, SecureRandom random,
                                                        SRPGroupParameter params) {
        byte[] I = username.getBytes();
        byte[] P = password.getBytes();
        byte[] s = new byte[16];
        random.nextBytes(s);

        SRP6VerifierGenerator gen = new SRP6VerifierGenerator();
        gen.init(params.N, params.g, new SHA256Digest());
        BigInteger v = gen.generateVerifier(s, I, P);
        return Pair.of(v.toString(16), s);
    }

    /**
     * Verifies the client and server secret.
     *
     * @param username     user name (aka "identity")
     * @param password     password
     * @param verification
     * @param random       the source of randomness for this generator
     * @param params       group parameters (prime, generator)
     * @return true if client and server secret is equals
     * @throws CryptoException If client or server's credentials are invalid
     */
    public static boolean checkMutualVerification(String username, String password,
                                                  Pair<String, byte[]> verification, SecureRandom random,
                                                  SRPGroupParameter params) throws CryptoException {

        byte[] I = username.getBytes();
        byte[] P = password.getBytes();
        byte[] s = verification.second;
        BigInteger v = new BigInteger(verification.first, 16);

        SRP6Client client = new SRP6Client();
        client.init(params.N, params.g, new SHA256Digest(), random);

        SRP6Server server = new SRP6Server();
        server.init(params.N, params.g, v, new SHA256Digest(), random);

        BigInteger A = client.generateClientCredentials(s, I, P);
        BigInteger B = server.generateServerCredentials();

        BigInteger clientS = client.calculateSecret(B);
        BigInteger serverS = server.calculateSecret(A);

        return clientS.equals(serverS);
    }

    /**
     * Load a class with a given name.
     * <p/>
     * It will try to load the class in the following order:
     * <ul>
     * <li>From Thread.currentThread().getContextClassLoader()
     * <li>Using the basic Class.forName()
     * <li>From SecurityUtil.class.getClassLoader()
     * <li>From the callingClass.getClassLoader()
     * </ul>
     *
     * @param className    The name of the class to load
     * @param callingClass The Class object of the calling object
     * @throws ClassNotFoundException If the class cannot be found anywhere.
     */
    public static Class<?> loadClass(String className, Class<?> callingClass)
            throws ClassNotFoundException {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();

            if (cl != null) {
                return cl.loadClass(className);
            }
        } catch (ClassNotFoundException e) {
            // ignore
        }
        return loadClass2(className, callingClass);
    }

    private static Class<?> loadClass2(String className, Class<?> callingClass)
            throws ClassNotFoundException {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            try {
                if (SecurityUtil.class.getClassLoader() != null) {
                    return SecurityUtil.class.getClassLoader().loadClass(className);
                }
            } catch (ClassNotFoundException exc) {
                if (callingClass != null && callingClass.getClassLoader() != null) {
                    return callingClass.getClassLoader().loadClass(className);
                }
            }
            throw ex;
        }
    }


    /**
     * Parse SRP Group Parameters
     *
     * @param N group parameters (prime)
     * @param g group parameters (generator)
     * @return new SRP Group Parameter
     * org.bouncycastle.crypto.params.SRP6GroupParameters
     */
    private static SRPGroupParameter parseSRPParameters(String N, int g) {
        return new SRPGroupParameter(N, g);
    }

    public static class SRPGroupParameter {
        private final BigInteger N;
        private final BigInteger g;

        private SRPGroupParameter(String N, int g) {
            this.N = new BigInteger(N.replaceAll(" ", ""), 16);
            this.g = BigInteger.valueOf(g);
        }

        public BigInteger N() {
            return N;
        }

        public BigInteger g() {
            return g;
        }
    }

    // 1024 bit example prime from RFC5054 and corresponding generator
    public static final SRPGroupParameter SRP_1024 = parseSRPParameters("" +
            "EEAF0AB9 ADB38DD6 9C33F80A FA8FC5E8 60726187 75FF3C0B 9EA2314C" +
            "9C256576 D674DF74 96EA81D3 383B4813 D692C6E0 E0D5D8E2 50B98BE4" +
            "8E495C1D 6089DAD1 5DC7D7B4 6154D6B6 CE8EF4AD 69B15D49 82559B29" +
            "7BCF1885 C529F566 660E57EC 68EDBC3C 05726CC0 2FD4CBF4 976EAA9A" +
            "FD5138FE 8376435B 9FC61D2F C0EB06E3", 2);

    // 1536 bit example prime from RFC5054 and corresponding generator
    public static final SRPGroupParameter SRP_1536 = parseSRPParameters("" +
            "9DEF3CAF B939277A B1F12A86 17A47BBB DBA51DF4 99AC4C80 BEEEA961" +
            "4B19CC4D 5F4F5F55 6E27CBDE 51C6A94B E4607A29 1558903B A0D0F843" +
            "80B655BB 9A22E8DC DF028A7C EC67F0D0 8134B1C8 B9798914 9B609E0B" +
            "E3BAB63D 47548381 DBC5B1FC 764E3F4B 53DD9DA1 158BFD3E 2B9C8CF5" +
            "6EDF0195 39349627 DB2FD53D 24B7C486 65772E43 7D6C7F8C E442734A" +
            "F7CCB7AE 837C264A E3A9BEB8 7F8A2FE9 B8B5292E 5A021FFF 5E91479E" +
            "8CE7A28C 2442C6F3 15180F93 499A234D CF76E3FE D135F9BB", 2);

    // 2048 bit example prime from RFC5054 and corresponding generator
    public static final SRPGroupParameter SRP_2048 = parseSRPParameters("" +
            "AC6BDB41 324A9A9B F166DE5E 1389582F AF72B665 1987EE07 FC319294" +
            "3DB56050 A37329CB B4A099ED 8193E075 7767A13D D52312AB 4B03310D" +
            "CD7F48A9 DA04FD50 E8083969 EDB767B0 CF609517 9A163AB3 661A05FB" +
            "D5FAAAE8 2918A996 2F0B93B8 55F97993 EC975EEA A80D740A DBF4FF74" +
            "7359D041 D5C33EA7 1D281E44 6B14773B CA97B43A 23FB8016 76BD207A" +
            "436C6481 F1D2B907 8717461A 5B9D32E6 88F87748 544523B5 24B0D57D" +
            "5EA77A27 75D2ECFA 032CFBDB F52FB378 61602790 04E57AE6 AF874E73" +
            "03CE5329 9CCC041C 7BC308D8 2A5698F3 A8D0C382 71AE35F8 E9DBFBB6" +
            "94B5C803 D89F7AE4 35DE236D 525F5475 9B65E372 FCD68EF2 0FA7111F" +
            "9E4AFF73", 2);

    // 3072 bit example prime from RFC5054 and corresponding generator
    public static final SRPGroupParameter SRP_3072 = parseSRPParameters("" +
            "FFFFFFFF FFFFFFFF C90FDAA2 2168C234 C4C6628B 80DC1CD1 29024E08" +
            "8A67CC74 020BBEA6 3B139B22 514A0879 8E3404DD EF9519B3 CD3A431B" +
            "302B0A6D F25F1437 4FE1356D 6D51C245 E485B576 625E7EC6 F44C42E9" +
            "A637ED6B 0BFF5CB6 F406B7ED EE386BFB 5A899FA5 AE9F2411 7C4B1FE6" +
            "49286651 ECE45B3D C2007CB8 A163BF05 98DA4836 1C55D39A 69163FA8" +
            "FD24CF5F 83655D23 DCA3AD96 1C62F356 208552BB 9ED52907 7096966D" +
            "670C354E 4ABC9804 F1746C08 CA18217C 32905E46 2E36CE3B E39E772C" +
            "180E8603 9B2783A2 EC07A28F B5C55DF0 6F4C52C9 DE2BCBF6 95581718" +
            "3995497C EA956AE5 15D22618 98FA0510 15728E5A 8AAAC42D AD33170D" +
            "04507A33 A85521AB DF1CBA64 ECFB8504 58DBEF0A 8AEA7157 5D060C7D" +
            "B3970F85 A6E1E4C7 ABF5AE8C DB0933D7 1E8C94E0 4A25619D CEE3D226" +
            "1AD2EE6B F12FFA06 D98A0864 D8760273 3EC86A64 521F2B18 177B200C" +
            "BBE11757 7A615D6C 770988C0 BAD946E2 08E24FA0 74E5AB31 43DB5BFC" +
            "E0FD108E 4B82D120 A93AD2CA FFFFFFFF FFFFFFFF", 5);

    // 4096 bit example prime from RFC5054 and corresponding generator
    public static final SRPGroupParameter SRP_4096 = parseSRPParameters("" +
            "FFFFFFFF FFFFFFFF C90FDAA2 2168C234 C4C6628B 80DC1CD1 29024E08" +
            "8A67CC74 020BBEA6 3B139B22 514A0879 8E3404DD EF9519B3 CD3A431B" +
            "302B0A6D F25F1437 4FE1356D 6D51C245 E485B576 625E7EC6 F44C42E9" +
            "A637ED6B 0BFF5CB6 F406B7ED EE386BFB 5A899FA5 AE9F2411 7C4B1FE6" +
            "49286651 ECE45B3D C2007CB8 A163BF05 98DA4836 1C55D39A 69163FA8" +
            "FD24CF5F 83655D23 DCA3AD96 1C62F356 208552BB 9ED52907 7096966D" +
            "670C354E 4ABC9804 F1746C08 CA18217C 32905E46 2E36CE3B E39E772C" +
            "180E8603 9B2783A2 EC07A28F B5C55DF0 6F4C52C9 DE2BCBF6 95581718" +
            "3995497C EA956AE5 15D22618 98FA0510 15728E5A 8AAAC42D AD33170D" +
            "04507A33 A85521AB DF1CBA64 ECFB8504 58DBEF0A 8AEA7157 5D060C7D" +
            "B3970F85 A6E1E4C7 ABF5AE8C DB0933D7 1E8C94E0 4A25619D CEE3D226" +
            "1AD2EE6B F12FFA06 D98A0864 D8760273 3EC86A64 521F2B18 177B200C" +
            "BBE11757 7A615D6C 770988C0 BAD946E2 08E24FA0 74E5AB31 43DB5BFC" +
            "E0FD108E 4B82D120 A9210801 1A723C12 A787E6D7 88719A10 BDBA5B26" +
            "99C32718 6AF4E23C 1A946834 B6150BDA 2583E9CA 2AD44CE8 DBBBC2DB" +
            "04DE8EF9 2E8EFC14 1FBECAA6 287C5947 4E6BC05D 99B2964F A090C3A2" +
            "233BA186 515BE7ED 1F612970 CEE2D7AF B81BDD76 2170481C D0069127" +
            "D5B05AA9 93B4EA98 8D8FDDC1 86FFB7DC 90A6C08F 4DF435C9 34063199" +
            "FFFFFFFF FFFFFFFF", 5);

    // 6144 bit example prime from RFC5054 and corresponding generator
    public static final SRPGroupParameter SRP_6144 = parseSRPParameters("" +
            "FFFFFFFF FFFFFFFF C90FDAA2 2168C234 C4C6628B 80DC1CD1 29024E08" +
            "8A67CC74 020BBEA6 3B139B22 514A0879 8E3404DD EF9519B3 CD3A431B" +
            "302B0A6D F25F1437 4FE1356D 6D51C245 E485B576 625E7EC6 F44C42E9" +
            "A637ED6B 0BFF5CB6 F406B7ED EE386BFB 5A899FA5 AE9F2411 7C4B1FE6" +
            "49286651 ECE45B3D C2007CB8 A163BF05 98DA4836 1C55D39A 69163FA8" +
            "FD24CF5F 83655D23 DCA3AD96 1C62F356 208552BB 9ED52907 7096966D" +
            "670C354E 4ABC9804 F1746C08 CA18217C 32905E46 2E36CE3B E39E772C" +
            "180E8603 9B2783A2 EC07A28F B5C55DF0 6F4C52C9 DE2BCBF6 95581718" +
            "3995497C EA956AE5 15D22618 98FA0510 15728E5A 8AAAC42D AD33170D" +
            "04507A33 A85521AB DF1CBA64 ECFB8504 58DBEF0A 8AEA7157 5D060C7D" +
            "B3970F85 A6E1E4C7 ABF5AE8C DB0933D7 1E8C94E0 4A25619D CEE3D226" +
            "1AD2EE6B F12FFA06 D98A0864 D8760273 3EC86A64 521F2B18 177B200C" +
            "BBE11757 7A615D6C 770988C0 BAD946E2 08E24FA0 74E5AB31 43DB5BFC" +
            "E0FD108E 4B82D120 A9210801 1A723C12 A787E6D7 88719A10 BDBA5B26" +
            "99C32718 6AF4E23C 1A946834 B6150BDA 2583E9CA 2AD44CE8 DBBBC2DB" +
            "04DE8EF9 2E8EFC14 1FBECAA6 287C5947 4E6BC05D 99B2964F A090C3A2" +
            "233BA186 515BE7ED 1F612970 CEE2D7AF B81BDD76 2170481C D0069127" +
            "D5B05AA9 93B4EA98 8D8FDDC1 86FFB7DC 90A6C08F 4DF435C9 34028492" +
            "36C3FAB4 D27C7026 C1D4DCB2 602646DE C9751E76 3DBA37BD F8FF9406" +
            "AD9E530E E5DB382F 413001AE B06A53ED 9027D831 179727B0 865A8918" +
            "DA3EDBEB CF9B14ED 44CE6CBA CED4BB1B DB7F1447 E6CC254B 33205151" +
            "2BD7AF42 6FB8F401 378CD2BF 5983CA01 C64B92EC F032EA15 D1721D03" +
            "F482D7CE 6E74FEF6 D55E702F 46980C82 B5A84031 900B1C9E 59E7C97F" +
            "BEC7E8F3 23A97A7E 36CC88BE 0F1D45B7 FF585AC5 4BD407B2 2B4154AA" +
            "CC8F6D7E BF48E1D8 14CC5ED2 0F8037E0 A79715EE F29BE328 06A1D58B" +
            "B7C5DA76 F550AA3D 8A1FBFF0 EB19CCB1 A313D55C DA56C9EC 2EF29632" +
            "387FE8D7 6E3C0468 043E8F66 3F4860EE 12BF2D5B 0B7474D6 E694F91E" +
            "6DCC4024 FFFFFFFF FFFFFFFF", 5);

    // 8192 bit example prime from RFC5054 and corresponding generator
    public static final SRPGroupParameter SRP_8192 = parseSRPParameters("" +
            "FFFFFFFF FFFFFFFF C90FDAA2 2168C234 C4C6628B 80DC1CD1 29024E08" +
            "8A67CC74 020BBEA6 3B139B22 514A0879 8E3404DD EF9519B3 CD3A431B" +
            "302B0A6D F25F1437 4FE1356D 6D51C245 E485B576 625E7EC6 F44C42E9" +
            "A637ED6B 0BFF5CB6 F406B7ED EE386BFB 5A899FA5 AE9F2411 7C4B1FE6" +
            "49286651 ECE45B3D C2007CB8 A163BF05 98DA4836 1C55D39A 69163FA8" +
            "FD24CF5F 83655D23 DCA3AD96 1C62F356 208552BB 9ED52907 7096966D" +
            "670C354E 4ABC9804 F1746C08 CA18217C 32905E46 2E36CE3B E39E772C" +
            "180E8603 9B2783A2 EC07A28F B5C55DF0 6F4C52C9 DE2BCBF6 95581718" +
            "3995497C EA956AE5 15D22618 98FA0510 15728E5A 8AAAC42D AD33170D" +
            "04507A33 A85521AB DF1CBA64 ECFB8504 58DBEF0A 8AEA7157 5D060C7D" +
            "B3970F85 A6E1E4C7 ABF5AE8C DB0933D7 1E8C94E0 4A25619D CEE3D226" +
            "1AD2EE6B F12FFA06 D98A0864 D8760273 3EC86A64 521F2B18 177B200C" +
            "BBE11757 7A615D6C 770988C0 BAD946E2 08E24FA0 74E5AB31 43DB5BFC" +
            "E0FD108E 4B82D120 A9210801 1A723C12 A787E6D7 88719A10 BDBA5B26" +
            "99C32718 6AF4E23C 1A946834 B6150BDA 2583E9CA 2AD44CE8 DBBBC2DB" +
            "04DE8EF9 2E8EFC14 1FBECAA6 287C5947 4E6BC05D 99B2964F A090C3A2" +
            "233BA186 515BE7ED 1F612970 CEE2D7AF B81BDD76 2170481C D0069127" +
            "D5B05AA9 93B4EA98 8D8FDDC1 86FFB7DC 90A6C08F 4DF435C9 34028492" +
            "36C3FAB4 D27C7026 C1D4DCB2 602646DE C9751E76 3DBA37BD F8FF9406" +
            "AD9E530E E5DB382F 413001AE B06A53ED 9027D831 179727B0 865A8918" +
            "DA3EDBEB CF9B14ED 44CE6CBA CED4BB1B DB7F1447 E6CC254B 33205151" +
            "2BD7AF42 6FB8F401 378CD2BF 5983CA01 C64B92EC F032EA15 D1721D03" +
            "F482D7CE 6E74FEF6 D55E702F 46980C82 B5A84031 900B1C9E 59E7C97F" +
            "BEC7E8F3 23A97A7E 36CC88BE 0F1D45B7 FF585AC5 4BD407B2 2B4154AA" +
            "CC8F6D7E BF48E1D8 14CC5ED2 0F8037E0 A79715EE F29BE328 06A1D58B" +
            "B7C5DA76 F550AA3D 8A1FBFF0 EB19CCB1 A313D55C DA56C9EC 2EF29632" +
            "387FE8D7 6E3C0468 043E8F66 3F4860EE 12BF2D5B 0B7474D6 E694F91E" +
            "6DBE1159 74A3926F 12FEE5E4 38777CB6 A932DF8C D8BEC4D0 73B931BA" +
            "3BC832B6 8D9DD300 741FA7BF 8AFC47ED 2576F693 6BA42466 3AAB639C" +
            "5AE4F568 3423B474 2BF1C978 238F16CB E39D652D E3FDB8BE FC848AD9" +
            "22222E04 A4037C07 13EB57A8 1A23F0C7 3473FC64 6CEA306B 4BCBC886" +
            "2F8385DD FA9D4B7F A2C087E8 79683303 ED5BDD3A 062B3CF5 B3A278A6" +
            "6D2A13F8 3F44F82D DF310EE0 74AB6A36 4597E899 A0255DC1 64F31CC5" +
            "0846851D F9AB4819 5DED7EA1 B1D510BD 7EE74D73 FAF36BC3 1ECFA268" +
            "359046F4 EB879F92 4009438B 481C6CD7 889A002E D5EE382B C9190DA6" +
            "FC026E47 9558E447 5677E9AA 9E3050E2 765694DF C81F56E8 80B96E71" +
            "60C980DD 98EDD3DF FFFFFFFF FFFFFFFF", 19);
}
