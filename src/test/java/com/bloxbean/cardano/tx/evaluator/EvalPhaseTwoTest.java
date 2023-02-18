package com.bloxbean.cardano.tx.evaluator;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.transaction.spec.Redeemer;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.tx.evaluator.jna.CardanoJNAUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class EvalPhaseTwoTest {

    @Test
    void eval_phase_two() {
        String tx_hex = "84a600818258202da783ed1161732f1f59319eca1f9da38afde36445ae065b2250a8e6e7362cfe000181825839000d30c6d716fd6c48ab546f0b66fd5faaa3a2f0ccecf0a72ea8c04a30a91cf775fb8e1fdfe882b26014f11d56bd47681270a55fd15e6d064c1a003d090002000d818258202da783ed1161732f1f59319eca1f9da38afde36445ae065b2250a8e6e7362cfe0110825839000d30c6d716fd6c48ab546f0b66fd5faaa3a2f0ccecf0a72ea8c04a30a91cf775fb8e1fdfe882b26014f11d56bd47681270a55fd15e6d064c821b00000002b64c0608a3581c4be3ff6f6ac1303b65adaa8c31b73395f488a6c3370b9694e6df21daa14d4275726e546f6b656e54657374185d581c689903d80b71e0570fea2fdaaa4bf80989785ed2a2cd57da0d9a7d0aa148746f6b656e5f3132197530581cc48f707fea6f08af67a8c06c9bea5b3ec847f5901dc08420cd7f8adea14a4f676d696f73546573741b0000001bf08eb000111a000f4240a20581840000182482000006815907a65907a3010000323322323232323232323232323232323322323232323222232325335323232333573466e1ccc07000d200000201e01d3333573466e1cd55cea80224000466442466002006004646464646464646464646464646666ae68cdc39aab9d500c480008cccccccccccc88888888888848cccccccccccc00403403002c02802402001c01801401000c008cd405c060d5d0a80619a80b80c1aba1500b33501701935742a014666aa036eb94068d5d0a804999aa80dbae501a35742a01066a02e0446ae85401cccd5406c08dd69aba150063232323333573466e1cd55cea801240004664424660020060046464646666ae68cdc39aab9d5002480008cc8848cc00400c008cd40b5d69aba15002302e357426ae8940088c98c80c0cd5ce01881801709aab9e5001137540026ae854008c8c8c8cccd5cd19b8735573aa004900011991091980080180119a816bad35742a004605c6ae84d5d1280111931901819ab9c03103002e135573ca00226ea8004d5d09aba2500223263202c33573805a05805426aae7940044dd50009aba1500533501775c6ae854010ccd5406c07c8004d5d0a801999aa80dbae200135742a00460426ae84d5d1280111931901419ab9c029028026135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d55cf280089baa00135742a00860226ae84d5d1280211931900d19ab9c01b01a018375a00a6eb4014405c4c98c805ccd5ce24810350543500017135573ca00226ea800448c88c008dd6000990009aa80b911999aab9f0012500a233500930043574200460066ae880080508c8c8cccd5cd19b8735573aa004900011991091980080180118061aba150023005357426ae8940088c98c8050cd5ce00a80a00909aab9e5001137540024646464646666ae68cdc39aab9d5004480008cccc888848cccc00401401000c008c8c8c8cccd5cd19b8735573aa0049000119910919800801801180a9aba1500233500f014357426ae8940088c98c8064cd5ce00d00c80b89aab9e5001137540026ae854010ccd54021d728039aba150033232323333573466e1d4005200423212223002004357426aae79400c8cccd5cd19b875002480088c84888c004010dd71aba135573ca00846666ae68cdc3a801a400042444006464c6403666ae7007006c06406005c4d55cea80089baa00135742a00466a016eb8d5d09aba2500223263201533573802c02a02626ae8940044d5d1280089aab9e500113754002266aa002eb9d6889119118011bab00132001355014223233335573e0044a010466a00e66442466002006004600c6aae754008c014d55cf280118021aba200301213574200222440042442446600200800624464646666ae68cdc3a800a40004642446004006600a6ae84d55cf280191999ab9a3370ea0049001109100091931900819ab9c01101000e00d135573aa00226ea80048c8c8cccd5cd19b875001480188c848888c010014c01cd5d09aab9e500323333573466e1d400920042321222230020053009357426aae7940108cccd5cd19b875003480088c848888c004014c01cd5d09aab9e500523333573466e1d40112000232122223003005375c6ae84d55cf280311931900819ab9c01101000e00d00c00b135573aa00226ea80048c8c8cccd5cd19b8735573aa004900011991091980080180118029aba15002375a6ae84d5d1280111931900619ab9c00d00c00a135573ca00226ea80048c8cccd5cd19b8735573aa002900011bae357426aae7940088c98c8028cd5ce00580500409baa001232323232323333573466e1d4005200c21222222200323333573466e1d4009200a21222222200423333573466e1d400d2008233221222222233001009008375c6ae854014dd69aba135744a00a46666ae68cdc3a8022400c4664424444444660040120106eb8d5d0a8039bae357426ae89401c8cccd5cd19b875005480108cc8848888888cc018024020c030d5d0a8049bae357426ae8940248cccd5cd19b875006480088c848888888c01c020c034d5d09aab9e500b23333573466e1d401d2000232122222223005008300e357426aae7940308c98c804ccd5ce00a00980880800780700680600589aab9d5004135573ca00626aae7940084d55cf280089baa0012323232323333573466e1d400520022333222122333001005004003375a6ae854010dd69aba15003375a6ae84d5d1280191999ab9a3370ea0049000119091180100198041aba135573ca00c464c6401866ae700340300280244d55cea80189aba25001135573ca00226ea80048c8c8cccd5cd19b875001480088c8488c00400cdd71aba135573ca00646666ae68cdc3a8012400046424460040066eb8d5d09aab9e500423263200933573801401200e00c26aae7540044dd500089119191999ab9a3370ea00290021091100091999ab9a3370ea00490011190911180180218031aba135573ca00846666ae68cdc3a801a400042444004464c6401466ae7002c02802001c0184d55cea80089baa0012323333573466e1d40052002200923333573466e1d40092000200923263200633573800e00c00800626aae74dd5000a4c240029210350543100320013550032225335333573466e1c0092000005004100113300333702004900119b80002001122002122001112323001001223300330020020011f5f6";
        String inputs = "818258202da783ed1161732f1f59319eca1f9da38afde36445ae065b2250a8e6e7362cfe00";
        String outputs = "81a300581d70b010c0888e93aa488d941ba4839136fceb9b9a9ec310a573299286d7011a003d0900028201d8184108";
        String cost_mdls = "a10198af1a0003236119032c01011903e819023b00011903e8195e7104011903e818201a0001ca761928eb041959d818641959d818641959d818641959d818641959d818641959d81864186418641959d81864194c5118201a0002acfa182019b551041a000363151901ff00011a00015c3518201a000797751936f404021a0002ff941a0006ea7818dc0001011903e8196ff604021a0003bd081a00034ec5183e011a00102e0f19312a011a00032e801901a5011a0002da781903e819cf06011a00013a34182019a8f118201903e818201a00013aac0119e143041903e80a1a00030219189c011a00030219189c011a0003207c1901d9011a000330001901ff0119ccf3182019fd40182019ffd5182019581e18201940b318201a00012adf18201a0002ff941a0006ea7818dc0001011a00010f92192da7000119eabb18201a0002ff941a0006ea7818dc0001011a0002ff941a0006ea7818dc0001011a0011b22c1a0005fdde00021a000c504e197712041a001d6af61a0001425b041a00040c660004001a00014fab18201a0003236119032c010119a0de18201a00033d7618201979f41820197fb8182019a95d1820197df718201995aa18201b00000004a817c8001b00000004a817c8001a009063b91903fd0a1b00000004a817c800001b00000004a817c800";

        SlotConfig.SlotConfigByValue slotConfig = new SlotConfig.SlotConfigByValue();
        slotConfig.zero_time = 1660003200000L;
        slotConfig.zero_slot = 0;
        slotConfig.slot_length = 1000;

        String redeemers = CardanoJNAUtil.eval_phase_two_raw(tx_hex, inputs, outputs, cost_mdls, slotConfig );
        System.out.println(deserializeRedeemerArray(redeemers));

    }

    private List<Redeemer> deserializeRedeemerArray(String response) {
        try {
            byte[] redemeersBytes = HexUtil.decodeHexString(response);
            Array redeemerArray = (Array) CborSerializationUtil.deserialize(redemeersBytes);
            List<Redeemer> redeemerList = new ArrayList<>();
            for (DataItem redeemerDI : redeemerArray.getDataItems()) {
                if (redeemerDI == SimpleValue.BREAK)
                    continue;
                Redeemer redeemer = Redeemer.deserialize((Array) redeemerDI);
                redeemerList.add(redeemer);
            }

            return redeemerList;
        } catch (Exception e) {
            throw new CborRuntimeException(e);
        }
    }
}