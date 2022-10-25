package com.bloxbean.cardano.tx.evaluator.jna;

import com.bloxbean.cardano.tx.evaluator.ExBudget;
import com.bloxbean.cardano.tx.evaluator.SlotConfig;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

interface CardanoJNA extends Library {
    CardanoJNA INSTANCE = Native.load(LibraryUtil.getAikenWrapperLib(),
            CardanoJNA.class);

    Pointer eval_phase_two_raw(String txBytes, String inputs, String outputs, String costMdlsBytes, ExBudget.ExBudgetByValue initialBudget,
                                                                   SlotConfig.SlotConfigByValue slotConfig, boolean runPhaseOne);

    void dropCharPointer(Pointer pointer);
}

