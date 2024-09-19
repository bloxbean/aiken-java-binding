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
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.cardano.client.api.ScriptSupplier;
import lombok.NonNull;

import java.util.*;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.plutus.spec.Language.PLUTUS_V1;
import static com.bloxbean.cardano.client.plutus.spec.Language.PLUTUS_V2;

/**
 * Implements TransactionEvaluator to evaluate a transaction to get script costs using Aiken evaluator.
 * This is a wrapper around TxEvaluator.
 */
public class AikenTransactionEvaluator implements TransactionEvaluator {
    private UtxoSupplier utxoSupplier;
    private ProtocolParamsSupplier protocolParamsSupplier;
    private ScriptSupplier scriptSupplier;

    /**
     * Constructor
     *
     * @param backendService Backend service
     */
    public AikenTransactionEvaluator(@NonNull BackendService backendService) {
        this.utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        this.protocolParamsSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());
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
    }

    /**
     * Constructor
     * @param utxoSupplier Utxo supplier
     * @param protocolParamsSupplier Protocol params supplier
     * @param scriptSupplier Script supplier to provide additional scripts (e.g; scripts in reference inputs) to evaluate
     */
    public AikenTransactionEvaluator(@NonNull UtxoSupplier utxoSupplier, @NonNull ProtocolParamsSupplier protocolParamsSupplier,
                                     ScriptSupplier scriptSupplier) {
        this.utxoSupplier = utxoSupplier;
        this.protocolParamsSupplier = protocolParamsSupplier;
        this.scriptSupplier = scriptSupplier;
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

            Language language =
                    (transaction.getWitnessSet() != null && transaction.getWitnessSet().getPlutusV1Scripts() != null
                            && transaction.getWitnessSet().getPlutusV1Scripts().size() > 0) ?
                            PLUTUS_V1 : PLUTUS_V2;
            ProtocolParams protocolParams = protocolParamsSupplier.getProtocolParams();
            Optional<CostModel> costModelOptional =
                    CostModelUtil.getCostModelFromProtocolParams(protocolParams, language);
            if (!costModelOptional.isPresent())
                throw new ApiException("Cost model not found for language: " + language);

            CostMdls costMdls = new CostMdls();
            costMdls.add(costModelOptional.get());

            TxEvaluator txEvaluator = new TxEvaluator();
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
}
