package com.bloxbean.cardano.aiken.jna;

import com.bloxbean.cardano.aiken.tx.evaluator.InitialBudgetConfig;
import com.bloxbean.cardano.aiken.tx.evaluator.SlotConfig;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

interface CardanoJNA extends Library {
    CardanoJNA INSTANCE = Native.load(LibraryUtil.getAikenWrapperLib(),
            CardanoJNA.class);

    Pointer eval_phase_two(String txBytes,
                           String inputs,
                           String outputs,
                           String costMdlsBytes,
                           InitialBudgetConfig.InitialBudgetByReference initialBudgetConfig,
                           SlotConfig.SlotConfigByReference slotConfig);

    void dropCharPointer(Pointer pointer);
}

