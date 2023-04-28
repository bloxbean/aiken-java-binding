package com.bloxbean.cardano.aiken.tx.evaluator;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.coinselection.impl.LargestFirstUtxoSelectionStrategy;
import com.bloxbean.cardano.client.common.CardanoConstants;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.Output;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.function.TxSigner;
import com.bloxbean.cardano.client.function.helper.*;
import com.bloxbean.cardano.client.function.helper.model.ScriptCallContext;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.util.CostModelUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;

//The caller has to guess the sum of 0..datum_value to claim the locked fund.
public class GuessSumContractIntegrationTest {

    String senderMnemonic = "kit color frog trick speak employ suit sort bomb goddess jewel primary spoil fade person useless measure manage warfare reduce few scrub beyond era";

    Account sender = new Account(Networks.testnet(), senderMnemonic);

    String senderAddress = sender.baseAddress();

    BackendService backendService = new KoiosBackendService(com.bloxbean.cardano.client.backend.koios.Constants.KOIOS_PREPROD_URL);

    private UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());

    private ProtocolParamsSupplier protocolParamsSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());

    @Test
    public void invokeContract() throws Exception {
        System.out.println("Sender address:" + senderAddress);

        //Sum Script
        PlutusV2Script sumScript =
                PlutusV2Script.builder()
                        .cborHex("5907a65907a3010000323322323232323232323232323232323322323232323222232325335323232333573466e1ccc07000d200000201e01d3333573466e1cd55cea80224000466442466002006004646464646464646464646464646666ae68cdc39aab9d500c480008cccccccccccc88888888888848cccccccccccc00403403002c02802402001c01801401000c008cd405c060d5d0a80619a80b80c1aba1500b33501701935742a014666aa036eb94068d5d0a804999aa80dbae501a35742a01066a02e0446ae85401cccd5406c08dd69aba150063232323333573466e1cd55cea801240004664424660020060046464646666ae68cdc39aab9d5002480008cc8848cc00400c008cd40b5d69aba15002302e357426ae8940088c98c80c0cd5ce01881801709aab9e5001137540026ae854008c8c8c8cccd5cd19b8735573aa004900011991091980080180119a816bad35742a004605c6ae84d5d1280111931901819ab9c03103002e135573ca00226ea8004d5d09aba2500223263202c33573805a05805426aae7940044dd50009aba1500533501775c6ae854010ccd5406c07c8004d5d0a801999aa80dbae200135742a00460426ae84d5d1280111931901419ab9c029028026135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d5d1280089aba25001135744a00226ae8940044d55cf280089baa00135742a00860226ae84d5d1280211931900d19ab9c01b01a018375a00a6eb4014405c4c98c805ccd5ce24810350543500017135573ca00226ea800448c88c008dd6000990009aa80b911999aab9f0012500a233500930043574200460066ae880080508c8c8cccd5cd19b8735573aa004900011991091980080180118061aba150023005357426ae8940088c98c8050cd5ce00a80a00909aab9e5001137540024646464646666ae68cdc39aab9d5004480008cccc888848cccc00401401000c008c8c8c8cccd5cd19b8735573aa0049000119910919800801801180a9aba1500233500f014357426ae8940088c98c8064cd5ce00d00c80b89aab9e5001137540026ae854010ccd54021d728039aba150033232323333573466e1d4005200423212223002004357426aae79400c8cccd5cd19b875002480088c84888c004010dd71aba135573ca00846666ae68cdc3a801a400042444006464c6403666ae7007006c06406005c4d55cea80089baa00135742a00466a016eb8d5d09aba2500223263201533573802c02a02626ae8940044d5d1280089aab9e500113754002266aa002eb9d6889119118011bab00132001355014223233335573e0044a010466a00e66442466002006004600c6aae754008c014d55cf280118021aba200301213574200222440042442446600200800624464646666ae68cdc3a800a40004642446004006600a6ae84d55cf280191999ab9a3370ea0049001109100091931900819ab9c01101000e00d135573aa00226ea80048c8c8cccd5cd19b875001480188c848888c010014c01cd5d09aab9e500323333573466e1d400920042321222230020053009357426aae7940108cccd5cd19b875003480088c848888c004014c01cd5d09aab9e500523333573466e1d40112000232122223003005375c6ae84d55cf280311931900819ab9c01101000e00d00c00b135573aa00226ea80048c8c8cccd5cd19b8735573aa004900011991091980080180118029aba15002375a6ae84d5d1280111931900619ab9c00d00c00a135573ca00226ea80048c8cccd5cd19b8735573aa002900011bae357426aae7940088c98c8028cd5ce00580500409baa001232323232323333573466e1d4005200c21222222200323333573466e1d4009200a21222222200423333573466e1d400d2008233221222222233001009008375c6ae854014dd69aba135744a00a46666ae68cdc3a8022400c4664424444444660040120106eb8d5d0a8039bae357426ae89401c8cccd5cd19b875005480108cc8848888888cc018024020c030d5d0a8049bae357426ae8940248cccd5cd19b875006480088c848888888c01c020c034d5d09aab9e500b23333573466e1d401d2000232122222223005008300e357426aae7940308c98c804ccd5ce00a00980880800780700680600589aab9d5004135573ca00626aae7940084d55cf280089baa0012323232323333573466e1d400520022333222122333001005004003375a6ae854010dd69aba15003375a6ae84d5d1280191999ab9a3370ea0049000119091180100198041aba135573ca00c464c6401866ae700340300280244d55cea80189aba25001135573ca00226ea80048c8c8cccd5cd19b875001480088c8488c00400cdd71aba135573ca00646666ae68cdc3a8012400046424460040066eb8d5d09aab9e500423263200933573801401200e00c26aae7540044dd500089119191999ab9a3370ea00290021091100091999ab9a3370ea00490011190911180180218031aba135573ca00846666ae68cdc3a801a400042444004464c6401466ae7002c02802001c0184d55cea80089baa0012323333573466e1d40052002200923333573466e1d40092000200923263200633573800e00c00800626aae74dd5000a4c240029210350543100320013550032225335333573466e1c0092000005004100113300333702004900119b80002001122002122001112323001001223300330020020011")
                        .build();
        String scriptAddress = AddressProvider.getEntAddress(sumScript, Networks.testnet()).toBech32();
        System.out.println("Script address:" + scriptAddress);

        // 2. Lock fund with a datum (inlineDatum)
        PlutusData datum = BigIntPlutusData.of(8);

        lockFundWithInlineDatum(scriptAddress, datum); //CIP-32

        //3. Claim fund by guessing the sum
        //Get script utxo

        Utxo scriptUtxo = ScriptUtxoFinders.findFirstByDatumHashUsingDatum(utxoSupplier, scriptAddress, datum).orElseThrow();
        BigInteger claimAmount = scriptUtxo
                .getAmount().stream().filter(amount -> CardanoConstants.LOVELACE.equals(amount.getUnit()))
                .findFirst()
                .orElseThrow().getQuantity();

        Output output = Output.builder()
                .address(senderAddress)
                .assetName(CardanoConstants.LOVELACE)
                .qty(claimAmount)
                .build();

        ScriptCallContext scriptCallContext = ScriptCallContext
                .builder()
                .script(sumScript)
                .exUnits(ExUnits.builder()  //Exact exUnits will be calculated later
                        .mem(BigInteger.valueOf(0))
                        .steps(BigInteger.valueOf(0))
                        .build())
                .redeemer(BigIntPlutusData.of(36))
                .redeemerTag(RedeemerTag.Spend).build();

        // Find collaterals
        UtxoSelectionStrategy utxoSelectionStrategy = new LargestFirstUtxoSelectionStrategy(utxoSupplier);

        Set<Utxo> collateralUtxos =
                utxoSelectionStrategy.select(senderAddress, new Amount(CardanoConstants.LOVELACE, adaToLovelace(5)), Collections.emptySet());

        //Get reference input
        TransactionInput refInput = new TransactionInput("5a47b9a4276362000566ac5e58c18f315440a78a8cb0a8d1fe066e0012bcfbab", 0);
        //Hardcoding referenceInputUtxo for now. This will be removed after https://github.com/bloxbean/cardano-client-lib/pull/241
        Utxo referenceInputUtxo = Utxo.builder()
                .address("addr_test1wzcppsyg36f65jydjsd6fqu3xm7whxu6nmp3pftn9xfgd4ckah4da")
                .txHash(refInput.getTransactionId())
                .outputIndex(refInput.getIndex())
                .amount(List.of(new Amount(CardanoConstants.LOVELACE, adaToLovelace(9.34408))))
                .referenceScriptHash("b010c0888e93aa488d941ba4839136fceb9b9a9ec310a573299286d7")
                .build();

        TxBuilder contractTxBuilder = output.outputBuilder()
                .buildInputs(InputBuilders.createFromUtxos(List.of(scriptUtxo)))
                .andThen(InputBuilders.referenceInputsFrom(List.of(refInput)))
                .andThen(CollateralBuilders.collateralOutputs(senderAddress, Lists.newArrayList(collateralUtxos))) //CIP-40
                .andThen(ScriptCallContextProviders.createFromScriptCallContext(scriptCallContext))
                .andThen((context, txn) -> {
                    CostMdls costMdls = new CostMdls();
                    costMdls.add(CostModelUtil.PlutusV2CostModel);

                    //Fix required in cardano-client-lib to also include reference input utxo in the context
                    Set<Utxo> utxos = new HashSet<>(context.getUtxos());
                    utxos.add(referenceInputUtxo);

                    //Evaluate ExUnits
                    SlotConfig slotConfig = new SlotConfig(1000, 0, 100);
                    InitialBudgetConfig initialBudgetConfig = new InitialBudgetConfig(14000000L, 10000000000L);
                    TxEvaluator txEvaluator = new TxEvaluator(slotConfig, initialBudgetConfig);
                    List<Redeemer> redeemerList = txEvaluator.evaluateTx(txn, utxos, costMdls);
                    txn.getWitnessSet().getRedeemers().get(0).setExUnits(redeemerList.get(0).getExUnits());

                    System.out.println("ExUnits evaluation From Aiken:" + redeemerList);

                    txn.getWitnessSet().getPlutusV2Scripts().clear();
                })
                .andThen(BalanceTxBuilders.balanceTx(senderAddress, 2));

        TxBuilderContext txBuilderContext = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier);

        //Tx Build and Submit
        TxSigner signer = SignerProviders.signerFrom(sender);

        Transaction signedTx = txBuilderContext
                .buildAndSign(contractTxBuilder, signer);

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTx.serialize());

        System.out.println("Unlock Tx: " + result);

        Assertions.assertTrue(result.isSuccessful());
        waitForTransaction(result);
    }

    private void lockFundWithInlineDatum(String scriptAddress, PlutusData datum) throws ApiException, CborSerializationException {
        Output lockOutput = Output.builder()
                .address(scriptAddress)
                .assetName(CardanoConstants.LOVELACE)
                .qty(adaToLovelace(4))
                .datum(datum)
                .inlineDatum(true).build();

        TxBuilder lockFundTxBuilder = lockOutput.outputBuilder()
                .buildInputs(InputBuilders.createFromSender(senderAddress, senderAddress))
                .andThen(BalanceTxBuilders.balanceTx(senderAddress, 1));

        Transaction signedTx = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier)
                .buildAndSign(lockFundTxBuilder, SignerProviders.signerFrom(sender));

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTx.serialize());
        System.out.println("Lock Tx: " + result);

        Assertions.assertTrue(result.isSuccessful());
        waitForTransaction(result);
    }

    private void waitForTransaction(Result<String> result) {
        try {
            if (result.isSuccessful()) { //Wait for transaction to be mined
                int count = 0;
                while (count < 60) {
                    Result<TransactionContent> txnResult = backendService.getTransactionService().getTransaction(result.getValue());
                    if (txnResult.isSuccessful()) {
                        System.out.println(JsonUtil.getPrettyJson(txnResult.getValue()));
                        break;
                    } else {
                        System.out.println("Waiting for transaction to be mined ....");
                    }

                    count++;
                    Thread.sleep(2000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
