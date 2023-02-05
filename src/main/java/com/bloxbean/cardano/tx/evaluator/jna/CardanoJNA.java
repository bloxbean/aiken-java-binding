package com.bloxbean.cardano.tx.evaluator.jna;

import com.bloxbean.cardano.tx.evaluator.SlotConfig;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.StringArray;

interface CardanoJNA extends Library {
    CardanoJNA INSTANCE = Native.load(LibraryUtil.getAikenWrapperLib(),
            CardanoJNA.class);

    Pointer eval_phase_two(String txBytes, String inputs, String outputs, String costMdlsBytes,
                           SlotConfig.SlotConfigByValue slotConfig);

    void dropCharPointer(Pointer pointer);
}

