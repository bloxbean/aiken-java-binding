package com.bloxbean.cardano.aiken.jna;

import com.bloxbean.cardano.aiken.tx.evaluator.InitialBudgetConfig;
import com.bloxbean.cardano.aiken.tx.evaluator.SlotConfig;
import com.sun.jna.Pointer;

public class CardanoJNAUtil {

    public static String eval_phase_two_raw(String tx,
                                            String inputs,
                                            String outputs,
                                            String costMdls,
                                            InitialBudgetConfig.InitialBudgetByValue initialBudgetConfig,
                                            SlotConfig.SlotConfigByReference slotConfig) {
        Pointer pointer = CardanoJNA.INSTANCE.eval_phase_two(tx, inputs, outputs, costMdls, initialBudgetConfig, slotConfig);
        String result = pointer.getString(0);

        CardanoJNA.INSTANCE.dropCharPointer(pointer);

        return result;
    }

    public static String apply_params_to_plutus_script(String params, String scriptCompiledCode) {
        Pointer pointer = CardanoJNA.INSTANCE.apply_params_to_plutus_script(params, scriptCompiledCode);
        String result = pointer.getString(0);

        CardanoJNA.INSTANCE.dropCharPointer(pointer);

        return result;
    }

}

