package com.bloxbean.cardano.aiken;

import com.bloxbean.cardano.aiken.tx.evaluator.TxEvaluator;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionEvaluator;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.CostModelUtil;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.common.model.SlotConfig;
import com.bloxbean.cardano.client.common.model.SlotConfigs;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.cardano.client.api.ScriptSupplier;
import lombok.NonNull;

import java.util.*;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.plutus.spec.Language.*;

/**
 * Implements TransactionEvaluator to evaluate a transaction to get script costs using Aiken evaluator.
 * This is a wrapper around TxEvaluator.
 */
public class AikenTransactionEvaluator implements TransactionEvaluator {
    private UtxoSupplier utxoSupplier;
    private ProtocolParamsSupplier protocolParamsSupplier;
    private ScriptSupplier scriptSupplier;
    private final SlotConfig slotConfig;

    /**
     * Constructor for mainnet.
     * Use AikenTransactionEvaluator(@NonNull BackendService backendService, SlotConfig slotConfig)
     * if you need to run TransactionEvaluation for any non-mainnet network instead
     *
     * @param backendService Backend service
     */
    public AikenTransactionEvaluator(@NonNull BackendService backendService) {
        this(backendService, SlotConfigs.mainnet());
    }

    /**
     * Constructor
     *
     * @param backendService Backend service
     * @param slotConfig the slot config to use, useful for non-mainnet network tx evaluation
     */
    public AikenTransactionEvaluator(@NonNull BackendService backendService, SlotConfig slotConfig) {
        this.utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        this.protocolParamsSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());
        this.slotConfig = slotConfig;
    }

    /**
     * Constructor
     * @param backendService Backend service
     * @param scriptSupplier Script supplier to provide additional scripts (e.g; scripts in reference inputs) to evaluate
     */
    public AikenTransactionEvaluator(@NonNull BackendService backendService, ScriptSupplier scriptSupplier) {
        this.utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        this.protocolParamsSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());
        this.scriptSupplier = scriptSupplier;
        this.slotConfig = SlotConfigs.mainnet();
    }

    /**
     * Constructor
     *
     * @param utxoSupplier           Utxo supplier
     * @param protocolParamsSupplier Protocol params supplier
     */
    public AikenTransactionEvaluator(@NonNull UtxoSupplier utxoSupplier, @NonNull ProtocolParamsSupplier protocolParamsSupplier) {
        this.utxoSupplier = utxoSupplier;
        this.protocolParamsSupplier = protocolParamsSupplier;
        this.slotConfig = SlotConfigs.mainnet();
    }

    /**
     * Constructor
     * @param utxoSupplier Utxo supplier
     * @param protocolParamsSupplier Protocol params supplier
     * @param scriptSupplier Script supplier to provide additional scripts (e.g; scripts in reference inputs) to evaluate
     */
    public AikenTransactionEvaluator(@NonNull UtxoSupplier utxoSupplier, @NonNull ProtocolParamsSupplier protocolParamsSupplier,
                                     ScriptSupplier scriptSupplier) {
        this(utxoSupplier, protocolParamsSupplier, scriptSupplier, SlotConfigs.mainnet());
    }


    /**
     * Constructor
     * @param utxoSupplier Utxo supplier
     * @param protocolParamsSupplier Protocol params supplier
     * @param scriptSupplier Script supplier to provide additional scripts (e.g; scripts in reference inputs) to evaluate
     * * @param slotConfig the slot config to use, useful for non-mainnet network tx evaluation
     */
    public AikenTransactionEvaluator(@NonNull UtxoSupplier utxoSupplier, @NonNull ProtocolParamsSupplier protocolParamsSupplier,
                                     ScriptSupplier scriptSupplier, SlotConfig slotConfig) {
        this.utxoSupplier = utxoSupplier;
        this.protocolParamsSupplier = protocolParamsSupplier;
        this.scriptSupplier = scriptSupplier;
        this.slotConfig = slotConfig;
    }

    @Override
    public Result<List<EvaluationResult>> evaluateTx(byte[] cbor, Set<Utxo> inputUtxos) throws ApiException {
        try {
            Transaction transaction = Transaction.deserialize(cbor);

            Set<Utxo> utxos = new HashSet<>();
            //inputs
            for (TransactionInput input : transaction.getBody().getInputs()) {
                Utxo utxo = utxoSupplier.getTxOutput(input.getTransactionId(), input.getIndex())
                        .get();
                utxos.add(utxo);
            }

            List<PlutusScript> additionalScripts = new ArrayList<>();
            //reference inputs
            for (TransactionInput input : transaction.getBody().getReferenceInputs()) {
                Utxo utxo = utxoSupplier.getTxOutput(input.getTransactionId(), input.getIndex()).get();
                utxos.add(utxo);

                //Get reference input script
                if (utxo.getReferenceScriptHash() != null && scriptSupplier != null) {
                    scriptSupplier.getScript(utxo.getReferenceScriptHash()).ifPresent(additionalScripts::add);
                }
            }

            //The following initializations are required to avoid NPE in aiken-java-binding
            if (transaction.getWitnessSet() == null)
                transaction.setWitnessSet(new TransactionWitnessSet());

            if (transaction.getWitnessSet().getPlutusV1Scripts() == null)
                transaction.getWitnessSet().setPlutusV1Scripts(new ArrayList<>());

            if (transaction.getWitnessSet().getPlutusV2Scripts() == null)
                transaction.getWitnessSet().setPlutusV2Scripts(new ArrayList<>());

            var languages = new HashSet<Language>();
            //check plutus v1 present
            if (transaction.getWitnessSet() != null
                    && transaction.getWitnessSet().getPlutusV1Scripts() != null
                    && transaction.getWitnessSet().getPlutusV1Scripts().size() > 0) {
                languages.add(PLUTUS_V1);
            }

            //check plutus v2 present
            if (transaction.getWitnessSet() != null
                    && transaction.getWitnessSet().getPlutusV2Scripts() != null
                    && transaction.getWitnessSet().getPlutusV2Scripts().size() > 0) {
                languages.add(PLUTUS_V2);
            }

            //check if plutus v3 present
            if (transaction.getWitnessSet() != null
                    && transaction.getWitnessSet().getPlutusV3Scripts() != null
                    && transaction.getWitnessSet().getPlutusV3Scripts().size() > 0) {
                languages.add(PLUTUS_V3);
            }

            //Check in reference scripts
            for (PlutusScript script : additionalScripts) {
                if (script == null)
                    continue;
                if (script.getLanguage() == PLUTUS_V1) {
                    languages.add(PLUTUS_V1);
                } else if (script.getLanguage() == PLUTUS_V2) {
                    languages.add(PLUTUS_V2);
                } else if (script.getLanguage() == PLUTUS_V3) {
                    languages.add(PLUTUS_V3);
                } else {
                    throw new ApiException("Unsupported language: " + script.getLanguage());
                }
            }

            ProtocolParams protocolParams = protocolParamsSupplier.getProtocolParams();

            CostMdls costMdls = new CostMdls();
            languages.stream().map(language -> CostModelUtil.getCostModelFromProtocolParams(protocolParams, language))
                    .filter(Optional::isPresent)
                    .forEach(costModelOptional -> costMdls.add(costModelOptional.get()));

            TxEvaluator txEvaluator = new TxEvaluator(getSlotConfig());
            List<Redeemer> redeemers = txEvaluator.evaluateTx(transaction, utxos, additionalScripts, costMdls);
            if (redeemers == null)
                return Result.success("Error evaluating transaction");

            List<EvaluationResult> evaluationResults = redeemers.stream().map(redeemer -> EvaluationResult.builder()
                    .redeemerTag(redeemer.getTag())
                    .index(redeemer.getIndex().intValue())
                    .exUnits(redeemer.getExUnits())
                    .build()).collect(Collectors.toList());
            return Result.success(JsonUtil.getPrettyJson(evaluationResults)).withValue(evaluationResults);
        } catch (Exception e) {
            throw new ApiException("Error evaluating transaction", e);
        }
    }

    private com.bloxbean.cardano.aiken.tx.evaluator.SlotConfig getSlotConfig() {
        com.bloxbean.cardano.aiken.tx.evaluator.SlotConfig.SlotConfigByReference slotConfig = new com.bloxbean.cardano.aiken.tx.evaluator.SlotConfig.SlotConfigByReference();
        slotConfig.zero_time = this.slotConfig.getZeroTime();
        slotConfig.zero_slot = this.slotConfig.getZeroSlot();
        slotConfig.slot_length = this.slotConfig.getSlotLength();
        return slotConfig;
    }
}
