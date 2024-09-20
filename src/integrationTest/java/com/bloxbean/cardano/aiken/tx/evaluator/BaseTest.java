package com.bloxbean.cardano.aiken.tx.evaluator;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;

import java.util.List;
import java.util.Optional;

public class BaseTest {
    String senderMnemonic = "kit color frog trick speak employ suit sort bomb goddess jewel primary spoil fade person useless measure manage warfare reduce few scrub beyond era";
    Account sender = new Account(Networks.testnet(), senderMnemonic);
    String senderAddress = sender.baseAddress();

    String senderMnemonic2 = "term basket catalog layer swarm page evoke trap tenant execute town extend army crazy cabin hotel fall sock pepper false neutral skate sausage knife";
    Account sender2 = new Account(Networks.testnet(), senderMnemonic2);
    String senderAddress2 = sender2.baseAddress();

//    BackendService backendService = new KoiosBackendService(com.bloxbean.cardano.client.backend.koios.Constants.KOIOS_PREPROD_URL);
    BackendService backendService = new BFBackendService(Constants.BLOCKFROST_PREPROD_URL, System.getenv("BF_PROJECT_ID"));
//    BackendService backendService = new BFBackendService("http://localhost:8080/api/v1/", "Dummy Key");
    UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
    ProtocolParamsSupplier protocolParamsSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());

    protected void checkIfUtxoAvailable(String txHash, String address) {
        Optional<Utxo> utxo = Optional.empty();
        int count = 0;
        while (utxo.isEmpty()) {
            if (count++ >= 20)
                break;
            List<Utxo> utxos = new DefaultUtxoSupplier(backendService.getUtxoService()).getAll(address);
            utxo = utxos.stream().filter(u -> u.getTxHash().equals(txHash))
                    .findFirst();
            System.out.println("Try to get new output... txhash: " + txHash);
            try {
                Thread.sleep(1000);
            } catch (Exception e) {}
        }
    }
}
