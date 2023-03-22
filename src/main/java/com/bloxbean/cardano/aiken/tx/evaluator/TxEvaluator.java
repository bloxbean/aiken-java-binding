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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Evaluate script costs for a transaction.
 */
@Slf4j
public class TxEvaluator {

    private final SlotConfig.SlotConfigByReference slotConfig;
    private final InitialBudgetConfig.InitialBudgetByValue initialBudgetConfig;

    public TxEvaluator() {
        this.slotConfig = getDefaultSlotConfig();
        this.initialBudgetConfig = getDefaultInitialBudgetConfig();
    }

    public TxEvaluator(SlotConfig slotConfig, InitialBudgetConfig initialBudgetConfig) {
        this.slotConfig = new SlotConfig.SlotConfigByReference();
        this.slotConfig.zero_slot = slotConfig.zero_slot;
        this.slotConfig.zero_time = slotConfig.zero_time;
        this.slotConfig.slot_length = slotConfig.slot_length;

        this.initialBudgetConfig = new InitialBudgetConfig.InitialBudgetByValue();
        this.initialBudgetConfig.mem = initialBudgetConfig.mem;
        this.initialBudgetConfig.cpu = initialBudgetConfig.cpu;
    }

    /**
     * Evaluate script costs for a transaction
     * @param transaction Transaction
     * @param inputUtxos List utxos used in transaction inputs
     * @param costMdls Cost models
     * @return List of {@link Redeemer} with estimated script costs as {@link ExUnits}
     * @throws TxEvaluationException if script evaluation fails
     */
    public List<Redeemer> evaluateTx(Transaction transaction, Set<Utxo> inputUtxos, CostMdls costMdls) {
        List<PlutusScript> scripts = new ArrayList<>();
        scripts.addAll(transaction.getWitnessSet().getPlutusV1Scripts());
        scripts.addAll(transaction.getWitnessSet().getPlutusV2Scripts());

        List<TransactionInput> txInputs = transaction.getBody().getInputs();
        List<TransactionOutput> txOutputs = resolveTxInputs(txInputs, inputUtxos, scripts);

        Array inputArray = serialiseInputs(txInputs);
        Array outputArray = serialiseOutputs(txOutputs);

        try {
            String costMdlsHex = HexUtil.encodeHexString(CborSerializationUtil.serialize(costMdls.serialize()));
            String trxCbor = transaction.serializeToHex();
            String inputsCbor = HexUtil.encodeHexString(CborSerializationUtil.serialize(inputArray));
            String outputsCbor = HexUtil.encodeHexString(CborSerializationUtil.serialize(outputArray));

            String response = CardanoJNAUtil.eval_phase_two_raw(
                    trxCbor,
                    inputsCbor,
                    outputsCbor,
                    costMdlsHex,
                    initialBudgetConfig,
                    slotConfig
            );

            if (log.isTraceEnabled()) {
                log.trace("Transaction: " + trxCbor);
                log.trace("Inputs : " + inputsCbor);
                log.trace("Outputs : " + outputsCbor);
                log.trace("CostMdlsHex : " + costMdlsHex);
            }

            return Optional.ofNullable(response).map(r -> {
                if (r.isEmpty()) {
                    throw new TxEvaluationException("Fatal error while evaluating transaction, empty response.");
                }

                if (r.contains("RedeemerError")) {
                    throw new TxEvaluationException(r);
                }

                return deserializeRedeemerArray(r);
            })
            .orElseThrow(() -> new TxEvaluationException("Fatal error while evaluating transaction, null response."));
        } catch (TxEvaluationException e) {
            throw e;
        } catch (Exception e) {
            throw new TxEvaluationException("TxEvaluation failed", e);
        }
    }

    private static Array serialiseOutputs(List<TransactionOutput> txOutputs) {
        Array outputArray = new Array();
        txOutputs.forEach(txOutput -> {
            try {
                outputArray.add(txOutput.serialize());
            } catch (CborSerializationException | AddressExcepion e) {
                throw new CborRuntimeException(e);
            }
        });
        return outputArray;
    }

    private static Array serialiseInputs(List<TransactionInput> txInputs) {
        Array inputArray = new Array();
        txInputs.forEach(txInput -> {
            try {
                inputArray.add(txInput.serialize());
            } catch (CborSerializationException e) {
                throw new CborRuntimeException(e);
            }
        });
        return inputArray;
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

    private static SlotConfig.SlotConfigByReference getDefaultSlotConfig() {
        SlotConfig.SlotConfigByReference slotConfig = new SlotConfig.SlotConfigByReference();
        slotConfig.zero_time = 1660003200000L;
        slotConfig.zero_slot = 0;
        slotConfig.slot_length = 1000;

        return slotConfig;
    }

    private static InitialBudgetConfig.InitialBudgetByValue getDefaultInitialBudgetConfig() {
        InitialBudgetConfig.InitialBudgetByValue initialBudgetConfig = new InitialBudgetConfig.InitialBudgetByValue();
        initialBudgetConfig.mem = 14000000L;
        initialBudgetConfig.cpu = 10000000000L;

        return initialBudgetConfig;
    }

    private List<TransactionOutput> resolveTxInputs(List<TransactionInput> transactionInputs, Set<Utxo> utxos, List<PlutusScript> plutusScripts) {
        return transactionInputs.stream().map(input -> {
            try {
                Utxo utxo = utxos.stream()
                        .filter(_utxo -> input.getTransactionId().equals(_utxo.getTxHash()))
                        .filter(_utxo -> input.getIndex() == _utxo.getOutputIndex())
                        .findFirst()
                        .orElseThrow();

                String address = utxo.getAddress();

                // Calculate script ref
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
