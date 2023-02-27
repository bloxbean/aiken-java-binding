package com.bloxbean.cardano.aiken.jna;

import com.bloxbean.cardano.aiken.tx.evaluator.SlotConfig;
import com.sun.jna.Pointer;

public class CardanoJNAUtil {

    public static String eval_phase_two_raw(String tx, String inputs, String outputs, String costMdls, SlotConfig.SlotConfigByValue slotConfig) {
        Pointer pointer = CardanoJNA.INSTANCE.eval_phase_two(tx, inputs, outputs, costMdls, slotConfig);
        String result = pointer.getString(0);

        CardanoJNA.INSTANCE.dropCharPointer(pointer);
        return result;
    }
}

