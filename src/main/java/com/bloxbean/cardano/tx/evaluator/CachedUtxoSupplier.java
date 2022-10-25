package com.bloxbean.cardano.tx.evaluator;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.util.Tuple;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CachedUtxoSupplier extends DefaultUtxoSupplier {
    private UtxoService utxoService;
    //key = utxo id, value = owner, Utxo
    private Map<String, Tuple<String, Utxo>> utxoCache;

    public CachedUtxoSupplier(UtxoService utxoService) {
        super(utxoService);
        this.utxoCache = new HashMap<>();
    }

    @Override
    public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        List<Utxo> utxos = super.getPage(address, nrOfItems, page, order);
        utxos.forEach(utxo -> {
            utxoCache.put(getUtxoCacheKey(utxo.getTxHash(), utxo.getOutputIndex()), new Tuple<>(address, utxo));
        });

        return utxos;
    }

    public Tuple<String, Utxo> getUtxo(String txHash, int txIndex) {
        return utxoCache.get(getUtxoCacheKey(txHash, txIndex));
    }

    private String getUtxoCacheKey(String txHash, int index) {
        return txHash + "#" + index;
    }
}
