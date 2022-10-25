package com.bloxbean.cardano.tx.evaluator.jna;

import com.bloxbean.cardano.tx.evaluator.ExBudget;
import com.bloxbean.cardano.tx.evaluator.SlotConfig;
import com.sun.jna.Pointer;

public class CardanoJNAUtil {

    public static String eval_phase_two_raw(String tx, String inputs, String outputs, String costMdls, ExBudget.ExBudgetByValue initialBudget,
                                            SlotConfig.SlotConfigByValue slotConfig, boolean runPhaseOne) {
        Pointer pointer = CardanoJNA.INSTANCE.eval_phase_two_raw(tx, inputs, outputs, costMdls, initialBudget, slotConfig, runPhaseOne);
        String result = pointer.getString(0);

        CardanoJNA.INSTANCE.dropCharPointer(pointer);
        return result;
    }
}

