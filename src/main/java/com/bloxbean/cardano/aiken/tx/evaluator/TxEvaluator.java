package com.bloxbean.cardano.aiken.tx.evaluator;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import com.bloxbean.cardano.aiken.jna.CardanoJNAUtil;
import com.bloxbean.cardano.aiken.tx.evaluator.exception.TxEvaluationException;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Evaluate script costs for a transaction.
 */
@Slf4j
public class TxEvaluator {
    private final SlotConfig slotConfig;

    public TxEvaluator() {
        this.slotConfig = getDefaultSlotConfig();
    }

    public TxEvaluator(SlotConfig slotConfig) {
        this.slotConfig = new SlotConfig.SlotConfigByValue();
        this.slotConfig.zero_slot = slotConfig.zero_slot;
        this.slotConfig.zero_time = slotConfig.zero_time;
        this.slotConfig.slot_length = slotConfig.slot_length;
    }

    /**
     * Evaluate script costs for a transaction
     * @param transaction Transaction
     * @param utxos List utxos used in transaction inputs
     * @param costMdls Cost models
     * @return List of {@link Redeemer} with estimated script costs as {@link ExUnits}
     * @throws TxEvaluationException if script evaluation fails
     */
    public List<Redeemer> evaluateTx(Transaction transaction, Set<Utxo> utxos, CostMdls costMdls) {
        List<PlutusScript> scripts = new ArrayList<>();
        scripts.addAll(transaction.getWitnessSet().getPlutusV1Scripts());
        scripts.addAll(transaction.getWitnessSet().getPlutusV2Scripts());

        List<TransactionInput> txInputs = transaction.getBody().getInputs();
        List<TransactionOutput> txOutputs = resolveTxInputs(txInputs, utxos, scripts);

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

            if (log.isTraceEnabled()) {
                log.trace("Transaction: " + transaction.serializeToHex());
                log.trace("Inputs : " + HexUtil.encodeHexString(CborSerializationUtil.serialize(inputArray)));
                log.trace("Outputs : " + HexUtil.encodeHexString(CborSerializationUtil.serialize(outputArray)));
                log.trace("CostMdlsHex : " + costMdlsHex);
            }

            if (response != null && response.contains("RedeemerError"))
                throw new TxEvaluationException(response);
            else
                return deserializeRedeemerArray(response);
        } catch (TxEvaluationException e) {
            throw e;
        } catch (Exception e) {
            throw new TxEvaluationException("TxEvaluation Failed", e);
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
            throw new CborRuntimeException("Unable to parse evaluation result : " + response, e);
        }
    }

    private SlotConfig.SlotConfigByValue getDefaultSlotConfig() {
        SlotConfig.SlotConfigByValue slotConfig = new SlotConfig.SlotConfigByValue();
        slotConfig.zero_time = 1660003200000L;
        slotConfig.zero_slot = 0;
        slotConfig.slot_length = 1000;
        return slotConfig;
    }

    private List<TransactionOutput> resolveTxInputs(List<TransactionInput> transactionInputs, Set<Utxo> utxos, List<PlutusScript> plutusScripts) {
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
}
