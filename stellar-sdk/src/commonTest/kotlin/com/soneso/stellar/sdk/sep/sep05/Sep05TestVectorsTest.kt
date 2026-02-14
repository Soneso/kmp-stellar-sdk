/*
 * Copyright 2025 Soneso
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.soneso.stellar.sdk.sep.sep05

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SEP-5 specification test vectors.
 *
 * These tests verify the implementation against the official SEP-5 test vectors
 * defined in https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0005.md
 * and verified against the Flutter Stellar SDK implementation.
 *
 * All test vectors must pass for the implementation to be SEP-5 compliant.
 */
class Sep05TestVectorsTest {

    // ========== Test Vector 1: 12 words (no passphrase) ==========
    // Mnemonic: "illness spike retreat truth genius clock brain pass fit cave bargain toe"
    // BIP-39 Seed: e4a5a632e70943ae7f07659df1332160937fad82587216a4c64315a0fb39497ee4a01f76ddab4cba68147977f3a147b6ad584c41808e8238a07f6cc4b582f186

    @Test
    fun testVector1_12Words_SeedDerivation() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val seed = MnemonicUtils.mnemonicToSeed(phrase)

        val expectedSeedHex = "e4a5a632e70943ae7f07659df1332160937fad82587216a4c64315a0fb39497ee4a01f76ddab4cba68147977f3a147b6ad584c41808e8238a07f6cc4b582f186"
        assertEquals(expectedSeedHex, HexCodec.encode(seed))
    }

    @Test
    fun testVector1_12Words_AllAccounts() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val m = Mnemonic.from(phrase)

        val expectedAccounts = listOf(
            Pair("GDRXE2BQUC3AZNPVFSCEZ76NJ3WWL25FYFK6RGZGIEKWE4SOOHSUJUJ6", "SBGWSG6BTNCKCOB3DIFBGCVMUPQFYPA2G4O34RMTB343OYPXU5DJDVMN"),
            Pair("GBAW5XGWORWVFE2XTJYDTLDHXTY2Q2MO73HYCGB3XMFMQ562Q2W2GJQX", "SCEPFFWGAG5P2VX5DHIYK3XEMZYLTYWIPWYEKXFHSK25RVMIUNJ7CTIS"),
            Pair("GAY5PRAHJ2HIYBYCLZXTHID6SPVELOOYH2LBPH3LD4RUMXUW3DOYTLXW", "SDAILLEZCSA67DUEP3XUPZJ7NYG7KGVRM46XA7K5QWWUIGADUZCZWTJP"),
            Pair("GAOD5NRAEORFE34G5D4EOSKIJB6V4Z2FGPBCJNQI6MNICVITE6CSYIAE", "SBMWLNV75BPI2VB4G27RWOMABVRTSSF7352CCYGVELZDSHCXWCYFKXIX"),
            Pair("GBCUXLFLSL2JE3NWLHAWXQZN6SQC6577YMAU3M3BEMWKYPFWXBSRCWV4", "SCPCY3CEHMOP2TADSV2ERNNZBNHBGP4V32VGOORIEV6QJLXD5NMCJUXI"),
            Pair("GBRQY5JFN5UBG5PGOSUOL4M6D7VRMAYU6WW2ZWXBMCKB7GPT3YCBU2XZ", "SCK27SFHI3WUDOEMJREV7ZJQG34SCBR6YWCE6OLEXUS2VVYTSNGCRS6X"),
            Pair("GBY27SJVFEWR3DUACNBSMJB6T4ZPR4C7ZXSTHT6GMZUDL23LAM5S2PQX", "SDJ4WDPOQAJYR3YIAJOJP3E6E4BMRB7VZ4QAEGCP7EYVDW6NQD3LRJMZ"),
            Pair("GAY7T23Z34DWLSTEAUKVBPHHBUE4E3EMZBAQSLV6ZHS764U3TKUSNJOF", "SA3HXJUCE2N27TBIZ5JRBLEBF3TLPQEBINP47E6BTMIWW2RJ5UKR2B3L"),
            Pair("GDJTCF62UUYSAFAVIXHPRBR4AUZV6NYJR75INVDXLLRZLZQ62S44443R", "SCD5OSHUUC75MSJG44BAT3HFZL2HZMMQ5M4GPDL7KA6HJHV3FLMUJAME"),
            Pair("GBTVYYDIYWGUQUTKX6ZMLGSZGMTESJYJKJWAATGZGITA25ZB6T5REF44", "SCJGVMJ66WAUHQHNLMWDFGY2E72QKSI3XGSBYV6BANDFUFE7VY4XNXXR")
        )

        for ((index, expected) in expectedAccounts.withIndex()) {
            val keyPair = m.getKeyPair(index)
            assertEquals(
                expected.first,
                keyPair.getAccountId(),
                "Account ID mismatch at index $index"
            )
            assertEquals(
                expected.second,
                keyPair.getSecretSeed()!!.concatToString(),
                "Secret seed mismatch at index $index"
            )
        }

        m.close()
    }

    // ========== Test Vector 2: From hex seed ==========
    // Same seed as Test Vector 1, but loaded directly from hex

    @Test
    fun testVector2_FromHexSeed() = runTest {
        val seedHex = "e4a5a632e70943ae7f07659df1332160937fad82587216a4c64315a0fb39497ee4a01f76ddab4cba68147977f3a147b6ad584c41808e8238a07f6cc4b582f186"
        val m = Mnemonic.fromBip39HexSeed(seedHex)

        // Should produce same results as Test Vector 1
        assertEquals("GDRXE2BQUC3AZNPVFSCEZ76NJ3WWL25FYFK6RGZGIEKWE4SOOHSUJUJ6", m.getAccountId(0))
        assertEquals("GBAW5XGWORWVFE2XTJYDTLDHXTY2Q2MO73HYCGB3XMFMQ562Q2W2GJQX", m.getAccountId(1))

        val keyPair0 = m.getKeyPair(0)
        assertEquals("SBGWSG6BTNCKCOB3DIFBGCVMUPQFYPA2G4O34RMTB343OYPXU5DJDVMN", keyPair0.getSecretSeed()!!.concatToString())

        m.close()
    }

    // ========== Test Vector 3: 15 words (no passphrase) ==========
    // Mnemonic: "resource asthma orphan phone ice canvas fire useful arch jewel impose vague theory cushion top"
    // BIP-39 Seed: 7b36d4e725b48695c3ffd2b4b317d5552cb157c1a26c46d36a05317f0d3053eb8b3b6496ba39ebd9312d10e3f9937b47a6790541e7c577da027a564862e92811

    @Test
    fun testVector3_15Words_SeedDerivation() = runTest {
        val phrase = "resource asthma orphan phone ice canvas fire useful arch jewel impose vague theory cushion top"
        val seed = MnemonicUtils.mnemonicToSeed(phrase)

        val expectedSeedHex = "7b36d4e725b48695c3ffd2b4b317d5552cb157c1a26c46d36a05317f0d3053eb8b3b6496ba39ebd9312d10e3f9937b47a6790541e7c577da027a564862e92811"
        assertEquals(expectedSeedHex, HexCodec.encode(seed))
    }

    @Test
    fun testVector3_15Words_AllAccounts() = runTest {
        val phrase = "resource asthma orphan phone ice canvas fire useful arch jewel impose vague theory cushion top"
        val m = Mnemonic.from(phrase)

        val expectedAccounts = listOf(
            Pair("GAVXVW5MCK7Q66RIBWZZKZEDQTRXWCZUP4DIIFXCCENGW2P6W4OA34RH", "SAKS7I2PNDBE5SJSUSU2XLJ7K5XJ3V3K4UDFAHMSBQYPOKE247VHAGDB"),
            Pair("GDFCYVCICATX5YPJUDS22KM2GW5QU2KKSPPPT2IC5AQIU6TP3BZSLR5K", "SAZ2H5GLAVWCUWNPQMB6I3OHRI63T2ACUUAWSH7NAGYYPXGIOPLPW3Q4"),
            Pair("GAUA3XK3SGEQFNCBM423WIM5WCZ4CR4ZDPDFCYSFLCTODGGGJMPOHAAE", "SDVSSLPL76I33DKAI4LFTOAKCHJNCXUERGPCMVFT655Z4GRLWM6ZZTSC"),
            Pair("GAH3S77QXTAPZ77REY6LGFIJ2XWVXFOKXHCFLA6HQTL3POLVZJDHHUDM", "SCH56YSGOBYVBC6DO3ZI2PY62GBVXT4SEJSXJOBQYGC2GCEZSB5PEVBZ"),
            Pair("GCSCZVGV2Y3EQ2RATJ7TE6PVWTW5OH5SMG754AF6W6YM3KJF7RMNPB4Y", "SBWBM73VUNBGBMFD4E2BA7Q756AKVEAAVTQH34RYEUFD6X64VYL5KXQ2"),
            Pair("GDKWYAJE3W6PWCXDZNMFNFQSPTF6BUDANE6OVRYMJKBYNGL62VKKCNCC", "SAVS4CDQZI6PSA5DPCC42S5WLKYIPKXPCJSFYY4N3VDK25T2XX2BTGVX"),
            Pair("GCDTVB4XDLNX22HI5GUWHBXJFBCPB6JNU6ZON7E57FA3LFURS74CWDJH", "SDFC7WZT3GDQVQUQMXN7TC7UWDW5E3GSMFPHUT2TSTQ7RKWTRA4PLBAL"),
            Pair("GBTDPL5S4IOUQHDLCZ7I2UXJ2TEHO6DYIQ3F2P5OOP3IS7JSJI4UMHQJ", "SA6UO2FIYC6AS2MSDECLR6F7NKCJTG67F7R4LV2GYB4HCZYXJZRLPOBB"),
            Pair("GD3KWA24OIM7V3MZKDAVSLN3NBHGKVURNJ72ZCTAJSDTF7RIGFXPW5FQ", "SBDNHDDICLLMBIDZ2IF2D3LH44OVUGGAVHQVQ6BZQI5IQO6AB6KNJCOV"),
            Pair("GB3C6RRQB3V7EPDXEDJCMTS45LVDLSZQ46PTIGKZUY37DXXEOAKJIWSV", "SDHRG2J34MGDAYHMOVKVJC6LX2QZMCTIKRO5I4JQ6BJQ36KVL6QUTT72")
        )

        for ((index, expected) in expectedAccounts.withIndex()) {
            val keyPair = m.getKeyPair(index)
            assertEquals(
                expected.first,
                keyPair.getAccountId(),
                "Account ID mismatch at index $index"
            )
            assertEquals(
                expected.second,
                keyPair.getSecretSeed()!!.concatToString(),
                "Secret seed mismatch at index $index"
            )
        }

        m.close()
    }

    // ========== Test Vector 4: 24 words (no passphrase) ==========
    // Mnemonic: "bench hurt jump file august wise shallow faculty impulse spring exact slush thunder author capable act festival slice deposit sauce coconut afford frown better"
    // BIP-39 Seed: 937ae91f6ab6f12461d9936dfc1375ea5312d097f3f1eb6fed6a82fbe38c85824da8704389831482db0433e5f6c6c9700ff1946aa75ad8cc2654d6e40f567866

    @Test
    fun testVector4_24Words_SeedDerivation() = runTest {
        val phrase = "bench hurt jump file august wise shallow faculty impulse spring exact slush thunder author capable act festival slice deposit sauce coconut afford frown better"
        val seed = MnemonicUtils.mnemonicToSeed(phrase)

        val expectedSeedHex = "937ae91f6ab6f12461d9936dfc1375ea5312d097f3f1eb6fed6a82fbe38c85824da8704389831482db0433e5f6c6c9700ff1946aa75ad8cc2654d6e40f567866"
        assertEquals(expectedSeedHex, HexCodec.encode(seed))
    }

    @Test
    fun testVector4_24Words_AllAccounts() = runTest {
        val phrase = "bench hurt jump file august wise shallow faculty impulse spring exact slush thunder author capable act festival slice deposit sauce coconut afford frown better"
        val m = Mnemonic.from(phrase)

        val expectedAccounts = listOf(
            Pair("GC3MMSXBWHL6CPOAVERSJITX7BH76YU252WGLUOM5CJX3E7UCYZBTPJQ", "SAEWIVK3VLNEJ3WEJRZXQGDAS5NVG2BYSYDFRSH4GKVTS5RXNVED5AX7"),
            Pair("GB3MTYFXPBZBUINVG72XR7AQ6P2I32CYSXWNRKJ2PV5H5C7EAM5YYISO", "SBKSABCPDWXDFSZISAVJ5XKVIEWV4M5O3KBRRLSPY3COQI7ZP423FYB4"),
            Pair("GDYF7GIHS2TRGJ5WW4MZ4ELIUIBINRNYPPAWVQBPLAZXC2JRDI4DGAKU", "SD5CCQAFRIPB3BWBHQYQ5SC66IB2AVMFNWWPBYGSUXVRZNCIRJ7IHESQ"),
            Pair("GAFLH7DGM3VXFVUID7JUKSGOYG52ZRAQPZHQASVCEQERYC5I4PPJUWBD", "SBSGSAIKEF7JYQWQSGXKB4SRHNSKDXTEI33WZDRR6UHYQCQ5I6ZGZQPK"),
            Pair("GAXG3LWEXWCAWUABRO6SMAEUKJXLB5BBX6J2KMHFRIWKAMDJKCFGS3NN", "SBIZH53PIRFTPI73JG7QYA3YAINOAT2XMNAUARB3QOWWVZVBAROHGXWM"),
            Pair("GA6RUD4DZ2NEMAQY4VZJ4C6K6VSEYEJITNSLUQKLCFHJ2JOGC5UCGCFQ", "SCVM6ZNVRUOP4NMCMMKLTVBEMAF2THIOMHPYSSMPCD2ZU7VDPARQQ6OY"),
            Pair("GCUDW6ZF5SCGCMS3QUTELZ6LSAH6IVVXNRPRLAUNJ2XYLCA7KH7ZCVQS", "SBSHUZQNC45IAIRSAHMWJEJ35RY7YNW6SMOEBZHTMMG64NKV7Y52ZEO2"),
            Pair("GBJ646Q524WGBN5X5NOAPIF5VQCR2WZCN6QZIDOSY6VA2PMHJ2X636G4", "SC2QO2K2B4EBNBJMBZIKOYSHEX4EZAZNIF4UNLH63AQYV6BE7SMYWC6E"),
            Pair("GDHX4LU6YBSXGYTR7SX2P4ZYZSN24VXNJBVAFOB2GEBKNN3I54IYSRM4", "SCGMC5AHAAVB3D4JXQPCORWW37T44XJZUNPEMLRW6DCOEARY3H5MAQST"),
            Pair("GDXOY6HXPIDT2QD352CH7VWX257PHVFR72COWQ74QE3TEV4PK2KCKZX7", "SCPA5OX4EYINOPAUEQCPY6TJMYICUS5M7TVXYKWXR3G5ZRAJXY3C37GF")
        )

        for ((index, expected) in expectedAccounts.withIndex()) {
            val keyPair = m.getKeyPair(index)
            assertEquals(
                expected.first,
                keyPair.getAccountId(),
                "Account ID mismatch at index $index"
            )
            assertEquals(
                expected.second,
                keyPair.getSecretSeed()!!.concatToString(),
                "Secret seed mismatch at index $index"
            )
        }

        m.close()
    }

    // ========== Test Vector 5: 24 words WITH passphrase ==========
    // Mnemonic: "cable spray genius state float twenty onion head street palace net private method loan turn phrase state blanket interest dry amazing dress blast tube"
    // Passphrase: "p4ssphr4se"
    // BIP-39 Seed: d425d39998fb42ce4cf31425f0eaec2f0a68f47655ea030d6d26e70200d8ff8bd4326b4bdf562ea8640a1501ae93ccd0fd7992116da5dfa24900e570a742a489

    @Test
    fun testVector5_24Words_WithPassphrase_SeedDerivation() = runTest {
        val phrase = "cable spray genius state float twenty onion head street palace net private method loan turn phrase state blanket interest dry amazing dress blast tube"
        val passphrase = "p4ssphr4se"
        val seed = MnemonicUtils.mnemonicToSeed(phrase, passphrase)

        val expectedSeedHex = "d425d39998fb42ce4cf31425f0eaec2f0a68f47655ea030d6d26e70200d8ff8bd4326b4bdf562ea8640a1501ae93ccd0fd7992116da5dfa24900e570a742a489"
        assertEquals(expectedSeedHex, HexCodec.encode(seed))
    }

    @Test
    fun testVector5_24Words_WithPassphrase_AllAccounts() = runTest {
        val phrase = "cable spray genius state float twenty onion head street palace net private method loan turn phrase state blanket interest dry amazing dress blast tube"
        val passphrase = "p4ssphr4se"
        val m = Mnemonic.from(phrase, passphrase = passphrase)

        val expectedAccounts = listOf(
            Pair("GDAHPZ2NSYIIHZXM56Y36SBVTV5QKFIZGYMMBHOU53ETUSWTP62B63EQ", "SAFWTGXVS7ELMNCXELFWCFZOPMHUZ5LXNBGUVRCY3FHLFPXK4QPXYP2X"),
            Pair("GDY47CJARRHHL66JH3RJURDYXAMIQ5DMXZLP3TDAUJ6IN2GUOFX4OJOC", "SBQPDFUGLMWJYEYXFRM5TQX3AX2BR47WKI4FDS7EJQUSEUUVY72MZPJF"),
            Pair("GCLAQF5H5LGJ2A6ACOMNEHSWYDJ3VKVBUBHDWFGRBEPAVZ56L4D7JJID", "SAF2LXRW6FOSVQNC4HHIIDURZL4SCGCG7UEGG23ZQG6Q2DKIGMPZV6BZ"),
            Pair("GBC36J4KG7ZSIQ5UOSJFQNUP4IBRN6LVUFAHQWT2ODEQ7Y3ASWC5ZN3B", "SDCCVBIYZDMXOR4VPC3IYMIPODNEDZCS44LDN7B5ZWECIE57N3BTV4GQ"),
            Pair("GA6NHA4KPH5LFYD6LZH35SIX3DU5CWU3GX6GCKPJPPTQCCQPP627E3CB", "SA5TRXTO7BG2Z6QTQT3O2LC7A7DLZZ2RBTGUNCTG346PLVSSHXPNDVNT"),
            Pair("GBOWMXTLABFNEWO34UJNSJJNVEF6ESLCNNS36S5SX46UZT2MNYJOLA5L", "SDEOED2KPHV355YNOLLDLVQB7HDPQVIGKXCAJMA3HTM4325ZHFZSKKUC"),
            Pair("GBL3F5JUZN3SQKZ7SL4XSXEJI2SNSVGO6WZWNJLG666WOJHNDDLEXTSZ", "SDYNO6TLFNV3IM6THLNGUG5FII4ET2H7NH3KCT6OAHIUSHKR4XBEEI6A"),
            Pair("GA5XPPWXL22HFFL5K5CE37CEPUHXYGSP3NNWGM6IK6K4C3EFHZFKSAND", "SDXMJXAY45W3WEFWMYEPLPIF4CXAD5ECQ37XKMGY5EKLM472SSRJXCYD"),
            Pair("GDS5I7L7LWFUVSYVAOHXJET2565MGGHJ4VHGVJXIKVKNO5D4JWXIZ3XU", "SAIZA26BUP55TDCJ4U7I2MSQEAJDPDSZSBKBPWQTD5OQZQSJAGNN2IQB"),
            Pair("GBOSMFQYKWFDHJWCMCZSMGUMWCZOM4KFMXXS64INDHVCJ2A2JAABCYRR", "SDXDYPDNRMGOF25AWYYKPHFAD3M54IT7LCLG7RWTGR3TS32A4HTUXNOS")
        )

        for ((index, expected) in expectedAccounts.withIndex()) {
            val keyPair = m.getKeyPair(index)
            assertEquals(
                expected.first,
                keyPair.getAccountId(),
                "Account ID mismatch at index $index"
            )
            assertEquals(
                expected.second,
                keyPair.getSecretSeed()!!.concatToString(),
                "Secret seed mismatch at index $index"
            )
        }

        m.close()
    }

    // ========== Test Vector 6: 12 words "abandon...about" (no passphrase) ==========
    // Mnemonic: "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    // BIP-39 Seed: 5eb00bbddcf069084889a8ab9155568165f5c453ccb85e70811aaed6f6da5fc19a5ac40b389cd370d086206dec8aa6c43daea6690f20ad3d8d48b2d2ce9e38e4

    @Test
    fun testVector6_12Words_AbandonAbout_SeedDerivation() = runTest {
        val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val seed = MnemonicUtils.mnemonicToSeed(phrase)

        val expectedSeedHex = "5eb00bbddcf069084889a8ab9155568165f5c453ccb85e70811aaed6f6da5fc19a5ac40b389cd370d086206dec8aa6c43daea6690f20ad3d8d48b2d2ce9e38e4"
        assertEquals(expectedSeedHex, HexCodec.encode(seed))
    }

    @Test
    fun testVector6_12Words_AbandonAbout_AllAccounts() = runTest {
        val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val m = Mnemonic.from(phrase)

        val expectedAccounts = listOf(
            Pair("GB3JDWCQJCWMJ3IILWIGDTQJJC5567PGVEVXSCVPEQOTDN64VJBDQBYX", "SBUV3MRWKNS6AYKZ6E6MOUVF2OYMON3MIUASWL3JLY5E3ISDJFELYBRZ"),
            Pair("GDVSYYTUAJ3ACHTPQNSTQBDQ4LDHQCMNY4FCEQH5TJUMSSLWQSTG42MV", "SCHDCVCWGAKGIMTORV6K5DYYV3BY4WG3RA4M6MCBGJLHUCWU2MC6DL66"),
            Pair("GBFPWBTN4AXHPWPTQVQBP4KRZ2YVYYOGRMV2PEYL2OBPPJDP7LECEVHR", "SAPLVTLUXSDLFRDGCCFLPDZMTCEVMP3ZXTM74EBJCVKZKM34LGQPF7K3"),
            Pair("GCCCOWAKYVFY5M6SYHOW33TSNC7Z5IBRUEU2XQVVT34CIZU7CXZ4OQ4O", "SDQYXOP2EAUZP4YOEQ5BUJIQ3RDSP5XV4ZFI6C5Y3QCD5Y63LWPXT7PW"),
            Pair("GCQ3J35MKPKJX7JDXRHC5YTXTULFMCBMZ5IC63EDR66QA3LO7264ZL7Q", "SCT7DUHYZD6DRCETT6M73GWKFJI4D56P3SNWNWNJ7ANLJZS6XIFYYXSB"),
            Pair("GDTA7622ZA5PW7F7JL7NOEFGW62M7GW2GY764EQC2TUJ42YJQE2A3QUL", "SDTWG5AFDI6GRQNLPWOC7IYS7AKOGMI2GX4OXTBTZHHYPMNZ2PX4ONWU"),
            Pair("GD7A7EACTPTBCYCURD43IEZXGIBCEXNBHN3OFWV2FOX67XKUIGRCTBNU", "SDJMWY4KFRS4PTA5WBFVCPS2GKYLXOMCLQSBNEIBG7KRGHNQOM25KMCP"),
            Pair("GAF4AGPVLQXFKEWQV3DZU5YEFU6YP7XJHAEEQH4G3R664MSF77FLLRK3", "SDOJH5JRCNGT57QTPTJEQGBEBZJPXE7XUDYDB24VTOPP7PH3ALKHAHFG"),
            Pair("GABTYCZJMCP55SS6I46SR76IHETZDLG4L37MLZRZKQDGBLS5RMP65TSX", "SC6N6GYQ2VA4T7CUP2BWGBRT2P6L2HQSZIUNQRHNDLISF6ND7TW4P4ER"),
            Pair("GAKFARYSPI33KUJE7HYLT47DCX2PFWJ77W3LZMRBPSGPGYPMSDBE7W7X", "SALJ5LPBTXCFML2CQ7ORP7WJNJOZSVBVRQAAODMVHMUF4P4XXFZB7MKY")
        )

        for ((index, expected) in expectedAccounts.withIndex()) {
            val keyPair = m.getKeyPair(index)
            assertEquals(
                expected.first,
                keyPair.getAccountId(),
                "Account ID mismatch at index $index"
            )
            assertEquals(
                expected.second,
                keyPair.getSecretSeed()!!.concatToString(),
                "Secret seed mismatch at index $index"
            )
        }

        m.close()
    }

    // ========== Additional Tests: 18 and 21 word mnemonics ==========
    // Note: SEP-5 spec does not include test vectors for 18 or 21 word mnemonics.
    // These tests verify basic functionality for these word lengths.

    @Test
    fun testAdditional_18Words_BasicFunctionality() = runTest {
        val phrase = Mnemonic.generate18WordsMnemonic()
        assertEquals(18, phrase.split(" ").size)
        assertTrue(Mnemonic.validate(phrase))

        val m = Mnemonic.from(phrase)
        val accounts = (0..4).map { m.getKeyPair(it) }
        val accountIds = accounts.map { it.getAccountId() }
        assertEquals(5, accountIds.toSet().size)

        m.close()
    }

    @Test
    fun testAdditional_21Words_BasicFunctionality() = runTest {
        val phrase = Mnemonic.generate21WordsMnemonic()
        assertEquals(21, phrase.split(" ").size)
        assertTrue(Mnemonic.validate(phrase))

        val m = Mnemonic.from(phrase)
        val accounts = (0..4).map { m.getKeyPair(it) }
        val accountIds = accounts.map { it.getAccountId() }
        assertEquals(5, accountIds.toSet().size)

        m.close()
    }

    // ========== Passphrase Difference Test ==========
    // Same mnemonic with vs without passphrase should produce different keys

    @Test
    fun testPassphraseDifference() = runTest {
        val phrase = "cable spray genius state float twenty onion head street palace net private method loan turn phrase state blanket interest dry amazing dress blast tube"

        val mnemonicNoPass = Mnemonic.from(phrase)
        val mnemonicWithPass = Mnemonic.from(phrase, passphrase = "p4ssphr4se")

        // Keys should be completely different
        assertTrue(
            mnemonicNoPass.getAccountId(0) != mnemonicWithPass.getAccountId(0),
            "Same mnemonic with different passphrases should produce different accounts"
        )

        // Verify the passphrase version matches known test vector
        assertEquals("GDAHPZ2NSYIIHZXM56Y36SBVTV5QKFIZGYMMBHOU53ETUSWTP62B63EQ", mnemonicWithPass.getAccountId(0))

        mnemonicNoPass.close()
        mnemonicWithPass.close()
    }

    // ========== Cross-Platform Consistency Test ==========

    @Test
    fun testCrossPlatformConsistency() = runTest {
        // Generate mnemonic
        val phrase = Mnemonic.generate24WordsMnemonic()

        // Create two Mnemonic instances from same phrase
        val mnemonic1 = Mnemonic.from(phrase)
        val mnemonic2 = Mnemonic.from(phrase)

        // Verify same results
        for (i in 0..9) {
            assertEquals(
                mnemonic1.getAccountId(i),
                mnemonic2.getAccountId(i),
                "Account ID should be consistent at index $i"
            )
            assertContentEquals(
                mnemonic1.getPublicKey(i),
                mnemonic2.getPublicKey(i),
                "Public key should be consistent at index $i"
            )
        }

        mnemonic1.close()
        mnemonic2.close()
    }

    // ========== Mnemonic Validation for All Test Vectors ==========

    @Test
    fun testVector1_MnemonicIsValid() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        assertTrue(Mnemonic.validate(phrase), "Test vector 1 mnemonic should be valid")
    }

    @Test
    fun testVector2_MnemonicIsValid() = runTest {
        val phrase = "resource asthma orphan phone ice canvas fire useful arch jewel impose vague theory cushion top"
        assertTrue(Mnemonic.validate(phrase), "Test vector 2 mnemonic should be valid")
    }

    @Test
    fun testVector3_MnemonicIsValid() = runTest {
        val phrase = "bench hurt jump file august wise shallow faculty impulse spring exact slush thunder author capable act festival slice deposit sauce coconut afford frown better"
        assertTrue(Mnemonic.validate(phrase), "Test vector 3 mnemonic should be valid")
    }

    @Test
    fun testVector4_MnemonicIsValid() = runTest {
        val phrase = "cable spray genius state float twenty onion head street palace net private method loan turn phrase state blanket interest dry amazing dress blast tube"
        assertTrue(Mnemonic.validate(phrase), "Test vector 4 mnemonic should be valid")
    }

    @Test
    fun testVector5_MnemonicIsValid() = runTest {
        val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        assertTrue(Mnemonic.validate(phrase), "Test vector 5 mnemonic should be valid")
    }

    // ========== BIP-39 Seed Size Test ==========

    @Test
    fun testAllMnemonicLengthsProduceSameSeedSize() = runTest {
        // All mnemonic lengths should produce 64-byte seeds
        val mnemonics = listOf(
            "illness spike retreat truth genius clock brain pass fit cave bargain toe", // 12 words (Test Vector 1)
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about", // 12 words (Test Vector 5)
            "resource asthma orphan phone ice canvas fire useful arch jewel impose vague theory cushion top", // 15 words (Test Vector 2)
            "bench hurt jump file august wise shallow faculty impulse spring exact slush thunder author capable act festival slice deposit sauce coconut afford frown better", // 24 words (Test Vector 3)
            "cable spray genius state float twenty onion head street palace net private method loan turn phrase state blanket interest dry amazing dress blast tube" // 24 words (Test Vector 4)
        )

        for (phrase in mnemonics) {
            val m = Mnemonic.from(phrase)
            val seed = m.getBip39Seed()
            assertEquals(64, seed.size, "BIP-39 seed should always be 64 bytes regardless of mnemonic length")
            m.close()
        }
    }

    // ========== Derived Key Signing Test ==========

    @Test
    fun testDerivedKeyCanSign() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
        val m = Mnemonic.from(phrase)

        val keyPair = m.getKeyPair(0)
        assertTrue(keyPair.canSign(), "Derived keypair should be able to sign")

        // Sign some data and verify
        val testData = "Hello, Stellar!".encodeToByteArray()
        val signature = keyPair.sign(testData)
        assertEquals(64, signature.size, "Ed25519 signature should be 64 bytes")

        // Verify signature
        assertTrue(keyPair.verify(testData, signature), "Signature verification should pass")

        m.close()
    }

    // ========== Seed Export/Import Consistency ==========

    @Test
    fun testSeedExportImportConsistency() = runTest {
        val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"

        // Create Mnemonic from phrase
        val mnemonic1 = Mnemonic.from(phrase)
        val seedHex = mnemonic1.getBip39SeedHex()

        // Create Mnemonic from exported seed
        val mnemonic2 = Mnemonic.fromBip39HexSeed(seedHex)

        // Both should produce the same keys
        for (i in 0..4) {
            assertEquals(
                mnemonic1.getAccountId(i),
                mnemonic2.getAccountId(i),
                "Account ID at index $i should match after seed export/import"
            )
        }

        mnemonic1.close()
        mnemonic2.close()
    }
}
