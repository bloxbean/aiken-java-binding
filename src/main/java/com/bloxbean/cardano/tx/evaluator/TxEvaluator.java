package com.bloxbean.cardano.tx.evaluator;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.tx.evaluator.jna.CardanoJNAUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TxEvaluator {
    private Set<Utxo> utxos;
    private SlotConfig slotConfig;

    public TxEvaluator(Set<Utxo> utxos) {
        this.utxos = utxos;
        this.slotConfig = getDefaultSlotConfig();
    }

    public TxEvaluator(Set<Utxo> utxos, SlotConfig slotConfig) {
        this.utxos = utxos;
        this.slotConfig = new SlotConfig.SlotConfigByValue();
        this.slotConfig.zero_slot = slotConfig.zero_slot;
        this.slotConfig.zero_time = slotConfig.zero_time;
        this.slotConfig.slot_length = slotConfig.slot_length;
    }

    public List<Redeemer> evaluateTx(Transaction transaction, CostMdls costMdls) {
        List<PlutusScript> scripts = new ArrayList<>();
        scripts.addAll(transaction.getWitnessSet().getPlutusV1Scripts());
        scripts.addAll(transaction.getWitnessSet().getPlutusV2Scripts());

        List<TransactionInput> txInputs = transaction.getBody().getInputs();
        List<TransactionOutput> txOutputs = resolveTxInputs(txInputs, scripts);

        //Serialize Inputs
        Array inputArray = new Array();
        txInputs.forEach(txInput -> {
            try {
                inputArray.add(txInput.serialize());
            } catch (CborSerializationException e) {
                throw new CborRuntimeException(e);
            }
        });

        //Serialize Outputs
        Array outputArray = new Array();
        txOutputs.forEach(txOutput -> {
            try {
                outputArray.add(txOutput.serialize());
            } catch (CborSerializationException | AddressExcepion e) {
                throw new CborRuntimeException(e);
            }
        });

        SlotConfig.SlotConfigByValue slotConfig = getDefaultSlotConfig();
        try {
            String costMdlsHex = HexUtil.encodeHexString(CborSerializationUtil.serialize(costMdls.serialize()));
            String response = CardanoJNAUtil.eval_phase_two_raw(transaction.serializeToHex(),
                    HexUtil.encodeHexString(CborSerializationUtil.serialize(inputArray)),
                    HexUtil.encodeHexString(CborSerializationUtil.serialize(outputArray)),
                    costMdlsHex, slotConfig);

            return deserializeRedeemerArray(response);
        } catch (CborSerializationException e) {
            throw new RuntimeException(e);
        } catch (CborException e) {
            throw new RuntimeException(e);
        }
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

    private SlotConfig.SlotConfigByValue getDefaultSlotConfig() {
        SlotConfig.SlotConfigByValue slotConfig = new SlotConfig.SlotConfigByValue();
        slotConfig.zero_time = 1660003200000L;
        slotConfig.zero_slot = 0;
        slotConfig.slot_length = 1000;
        return slotConfig;
    }

    private List<TransactionOutput> resolveTxInputs(List<TransactionInput> transactionInputs, List<PlutusScript> plutusScripts) {
        return transactionInputs.stream().map(input -> {
            try {

                Utxo utxo = utxos.stream().filter(_utxo -> input.getTransactionId().equals(_utxo.getTxHash()) && input.getIndex() == _utxo.getOutputIndex())
                        .findFirst()
                        .orElseThrow();

                String address = utxo.getAddress();

                //Calculate script ref
                PlutusScript plutusScript = plutusScripts.stream().filter(script -> {
                    try {
                        return HexUtil.encodeHexString(script.getScriptHash()).equals(utxo.getReferenceScriptHash());
                    } catch (CborSerializationException e) {
                        throw new IllegalStateException(e);
                    }
                }).findFirst().orElse(null);

                PlutusData inlineDatum = utxo.getInlineDatum() != null ? PlutusData.deserialize(HexUtil.decodeHexString(utxo.getInlineDatum())) : null;
                byte[] datumHash = utxo.getDataHash() != null ? HexUtil.decodeHexString(utxo.getDataHash()) : null;


                return TransactionOutput.builder()
                        .address(address)
                        .value(utxo.toValue())
                        .datumHash(inlineDatum == null ? datumHash : null)
                        .inlineDatum(inlineDatum)
                        .scriptRef(plutusScript)
                        .build();
            } catch (CborDeserializationException e) {
                throw new IllegalStateException(e);
            }

        }).collect(Collectors.toList());
    }

    public String evaluateTx(String txnHex, String inputs, String outputs, String costMdls) {
        SlotConfig.SlotConfigByValue slotConfig = getDefaultSlotConfig();
        String response = CardanoJNAUtil.eval_phase_two_raw(txnHex, inputs, outputs, costMdls, slotConfig);

        return response;
    }

    public static void main(String[] args) throws Exception {
//        String txHex = "84a80081825820975c17a4fed0051be622328efa548e206657d2b65a19224bf6ff8132571e6a5002018282581d60b6c8794e9a7a26599440a4d0fd79cd07644d15917ff13694f1f67235821a000f41f0a1581cc4f241450001af08f3ddbaf9335db79883cbcd81071b8e3508de3055a1400a82581d60b6c8794e9a7a26599440a4d0fd79cd07644d15917ff13694f1f672351a0084192f021a00053b6109a1581cc4f241450001af08f3ddbaf9335db79883cbcd81071b8e3508de3055a1400a0b5820b4f96b0acec8beff2adededa8ba317bcac92174f0f65ccefe569b9a6aac7375a0d818258206c732139de33e916342707de2aebef2252c781640326ff37b86ec99d97f1ba8d011082581d60b6c8794e9a7a26599440a4d0fd79cd07644d15917ff13694f1f672351b00000001af0cdfa2111a0007d912a3008182582031ae74f8058527afb305d7495b10a99422d9337fc199e1f28044f2c477a0f9465840b8b97b7c3b4e19ecfc2fcd9884ee53a35887ee6e4d36901b9ecbac3fe032d7e8a4358305afa573a86396e378255651ed03501906e9def450e588d4bb36f42a050581840100d87980821a000b68081a0cf3a5bf06815909b25909af010000323322323232323232323232323232323232323232332232323232323232323233223232223232533533223233025323233355300f1200135028502623500122333553012120013502b50292350012233350012330314800000488cc0c80080048cc0c400520000013355300e1200123500122335501c0023335001233553012120012350012233550200023550140010012233355500f0150020012335530121200123500122335502000235501300100133355500a01000200130105002300f5001533532350012222222222220045001102a2216135001220023333573466e1cd55ce9baa0044800080808c98c8080cd5ce01081000f1999ab9a3370e6aae7540092000233221233001003002323232323232323232323232323333573466e1cd55cea8062400046666666666664444444444442466666666666600201a01801601401201000e00c00a00800600466a03803a6ae854030cd4070074d5d0a80599a80e00f1aba1500a3335502075ca03e6ae854024ccd54081d7280f9aba1500833501c02835742a00e666aa040052eb4d5d0a8031919191999ab9a3370e6aae75400920002332212330010030023232323333573466e1cd55cea8012400046644246600200600466a066eb4d5d0a801181a1aba135744a004464c6406c66ae700dc0d80d04d55cf280089baa00135742a0046464646666ae68cdc39aab9d5002480008cc8848cc00400c008cd40cdd69aba150023034357426ae8940088c98c80d8cd5ce01b81b01a09aab9e5001137540026ae84d5d1280111931901919ab9c033032030135573ca00226ea8004d5d0a80299a80e3ae35742a008666aa04004a40026ae85400cccd54081d710009aba150023027357426ae8940088c98c80b8cd5ce01781701609aba25001135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d5d1280089aba25001135744a00226aae7940044dd50009aba150023017357426ae8940088c98c8080cd5ce01081000f080f89931900f99ab9c4901035054350001f135573ca00226ea8004444888ccd54c010480054040cd54c01c480048d400488cd54054008d54024004ccd54c0104800488d4008894cd4ccd54c03048004c8cd409c88ccd400c88008008004d40048800448cc004894cd400840b040040a48d400488cc028008014018400c4cd405001000d4044004cd54c01c480048d400488c8cd5405800cc004014c8004d540a4894cd40044d5402800c884d4008894cd4cc03000802044888cc0080280104c01800c008c8004d5408888448894cd40044008884cc014008ccd54c01c480040140100044484888c00c0104484888c004010c8004d5407c8844894cd400454038884cd403cc010008cd54c01848004010004c8004d5407888448894cd40044d400c88004884ccd401488008c010008ccd54c01c4800401401000488ccd5cd19b8f00200101e01d2350012222222222220091232230023758002640026aa038446666aae7c004940288cd4024c010d5d080118019aba2002015232323333573466e1cd55cea80124000466442466002006004601a6ae854008c014d5d09aba2500223263201533573802c02a02626aae7940044dd50009191919191999ab9a3370e6aae75401120002333322221233330010050040030023232323333573466e1cd55cea80124000466442466002006004602c6ae854008cd4040054d5d09aba2500223263201a33573803603403026aae7940044dd50009aba150043335500875ca00e6ae85400cc8c8c8cccd5cd19b875001480108c84888c008010d5d09aab9e500323333573466e1d4009200223212223001004375c6ae84d55cf280211999ab9a3370ea00690001091100191931900e19ab9c01d01c01a019018135573aa00226ea8004d5d0a80119a8063ae357426ae8940088c98c8058cd5ce00b80b00a09aba25001135744a00226aae7940044dd5000899aa800bae75a224464460046eac004c8004d5406488c8cccd55cf80112804119a80399aa80498031aab9d5002300535573ca00460086ae8800c04c4d5d08008891001091091198008020018891091980080180109119191999ab9a3370ea0029000119091180100198029aba135573ca00646666ae68cdc3a801240044244002464c6402066ae700440400380344d55cea80089baa001232323333573466e1d400520062321222230040053007357426aae79400c8cccd5cd19b875002480108c848888c008014c024d5d09aab9e500423333573466e1d400d20022321222230010053007357426aae7940148cccd5cd19b875004480008c848888c00c014dd71aba135573ca00c464c6402066ae7004404003803403002c4d55cea80089baa001232323333573466e1cd55cea80124000466442466002006004600a6ae854008dd69aba135744a004464c6401866ae700340300284d55cf280089baa0012323333573466e1cd55cea800a400046eb8d5d09aab9e500223263200a33573801601401026ea80048c8c8c8c8c8cccd5cd19b8750014803084888888800c8cccd5cd19b875002480288488888880108cccd5cd19b875003480208cc8848888888cc004024020dd71aba15005375a6ae84d5d1280291999ab9a3370ea00890031199109111111198010048041bae35742a00e6eb8d5d09aba2500723333573466e1d40152004233221222222233006009008300c35742a0126eb8d5d09aba2500923333573466e1d40192002232122222223007008300d357426aae79402c8cccd5cd19b875007480008c848888888c014020c038d5d09aab9e500c23263201333573802802602202001e01c01a01801626aae7540104d55cf280189aab9e5002135573ca00226ea80048c8c8c8c8cccd5cd19b875001480088ccc888488ccc00401401000cdd69aba15004375a6ae85400cdd69aba135744a00646666ae68cdc3a80124000464244600400660106ae84d55cf280311931900619ab9c00d00c00a009135573aa00626ae8940044d55cf280089baa001232323333573466e1d400520022321223001003375c6ae84d55cf280191999ab9a3370ea004900011909118010019bae357426aae7940108c98c8024cd5ce00500480380309aab9d50011375400224464646666ae68cdc3a800a40084244400246666ae68cdc3a8012400446424446006008600c6ae84d55cf280211999ab9a3370ea00690001091100111931900519ab9c00b00a008007006135573aa00226ea80048c8cccd5cd19b8750014800880348cccd5cd19b8750024800080348c98c8018cd5ce00380300200189aab9d37540029309000a4810350543100112330010020072253350021001100612335002223335003220020020013500122001122123300100300222333573466e1c00800401000c488008488004448c8c00400488cc00cc008008005f5f6";
//        Transaction tx = Transaction.deserialize(HexUtil.decodeHexString(txHex));
//
//        String inputs = "84825820b16778c9cf065d9efeefe37ec269b4fc5107ecdbd0dd6bf3274b224165c2edd9008258206c732139de33e916342707de2aebef2252c781640326ff37b86ec99d97f1ba8d01825820975c17a4fed0051be622328efa548e206657d2b65a19224bf6ff8132571e6a500282582018f86700660fc88d0370a8f95ea58f75507e6b27a18a17925ad3b1777eb0d77600";
//        String outputs = "8482581d60b6c8794e9a7a26599440a4d0fd79cd07644d15917ff13694f1f67235821a000f8548a1581c15be994a64bdb79dde7fe080d8e7ff81b33a9e4860e9ee0d857a8e85a144576177610182581d60b6c8794e9a7a26599440a4d0fd79cd07644d15917ff13694f1f672351b00000001af14b8b482581d60b6c8794e9a7a26599440a4d0fd79cd07644d15917ff13694f1f672351a0098968082581d60b6c8794e9a7a26599440a4d0fd79cd07644d15917ff13694f1f672351a00acd8c6";
//
//        Array inputDIs = (Array) CborSerializationUtil.deserialize(HexUtil.decodeHexString(inputs));
//        Array outputDIs = (Array) CborSerializationUtil.deserialize(HexUtil.decodeHexString(outputs));
//
//        List<TransactionInput> inputList = new ArrayList<>();
//        for (DataItem inputDI : inputDIs.getDataItems()) {
//            inputList.add(TransactionInput.deserialize((Array) inputDI));
//        }
//
//        List<TransactionOutput> outputList = new ArrayList<>();
//        for (DataItem outputDI : outputDIs.getDataItems()) {
//            outputList.add(TransactionOutput.deserialize(outputDI));
//        }
//
//        System.out.println(inputList);
//        System.out.println(outputList);
//
//        CostMdls costMdls = new CostMdls();
////        costMdls.add(CostModelUtil.PlutusV1CostModel);
//        costMdls.add(CostModelUtil.PlutusV2CostModel);
//        String costMdlsHex = HexUtil.encodeHexString(CborSerializationUtil.serialize(costMdls.serialize()));
//
//
//        TxEvaluator txEvaluator = new TxEvaluator(null);
//        String response = txEvaluator.evaluateTx(txHex, inputs, outputs, costMdlsHex);
//
//        System.out.println(response);
    }
}
