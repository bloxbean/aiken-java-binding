package com.bloxbean.cardano.aiken.tx.evaluator;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class GovScriptTest extends BaseTest {

    @Test
    void registerDrep() throws CborSerializationException {

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);

        PlutusV3Script plutusScript = PlutusV3Script.builder()
                .type("PlutusScriptV3")
                .cborHex("46450101002499")
                .build();

        var scriptHash = plutusScript.getScriptHash();
        var scriptCredential = Credential.fromScript(scriptHash);

        var anchor = new Anchor("https://pages.bloxbean.com/cardano-stake/bloxbean-pool.json",
                HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937"));

        ScriptTx drepRegTx = new ScriptTx()
                .registerDRep(scriptCredential, anchor, BigIntPlutusData.of(1))
                .attachCertificateValidator(plutusScript);

        Result<String> result = quickTxBuilder.compose(drepRegTx)
                .feePayer(senderAddress)
                .withSigner(SignerProviders.signerFrom(sender))
                .completeAndWait(System.out::println);

        System.out.println("DRepId : " + sender.drepId());

        System.out.println(result);
        assertTrue(result.isSuccessful());

        checkIfUtxoAvailable(result.getValue(), senderAddress);
    }
}
