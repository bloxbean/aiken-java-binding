package com.bloxbean.cardano.tx.evaluator;

import com.sun.jna.Structure;

import java.io.Closeable;
import java.io.IOException;

@Structure.FieldOrder({"mem", "cpu"})
public class ExBudget extends Structure implements Closeable {
    public static class ExBudgetByValue extends ExBudget implements Structure.ByValue { }
//    public static class ByReference extends ExBudget implements Structure.ByReference { }

    public long mem;
    public long cpu;

    @Override
    public void close() throws IOException {
        // Turn off "auto-synch". If it is on, JNA will automatically read all fields
        // from the struct's memory and update them on the Java object. This synchronization
        // occurs after every native method call. If it occurs after we drop the struct, JNA
        // will try to read from the freed memory and cause a segmentation fault.
        setAutoSynch(false);
        // Send the struct back to rust for the memory to be freed
        //Greetings.INSTANCE.dropGreeting(this);
    }
}
