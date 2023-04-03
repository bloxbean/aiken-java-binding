package com.bloxbean.cardano.aiken.tx.evaluator;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Redeemer;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.ArrayList;
import java.util.List;

final class AikenCbors {

    public static List<Redeemer> deserializeRedeemerArray(String response) {
        try {
            byte[] redemeersBytes = HexUtil.decodeHexString(response);
            Array redeemerArray = (Array) CborSerializationUtil.deserialize(redemeersBytes);
            List<Redeemer> redeemerList = new ArrayList<>();
            for (DataItem redeemerDI : redeemerArray.getDataItems()) {
                if (redeemerDI.equals(SimpleValue.BREAK))
                    continue;
                Redeemer redeemer = Redeemer.deserialize((Array) redeemerDI);
                redeemerList.add(redeemer);
            }

            return redeemerList;
        } catch (Exception e) {
            throw new CborRuntimeException("Unable to parse evaluation result: " + response, e);
        }
    }

    public static Array serialiseOutputs(List<TransactionOutput> txOutputs) {
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

    public static Array serialiseInputs(List<TransactionInput> txInputs) {
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

}
