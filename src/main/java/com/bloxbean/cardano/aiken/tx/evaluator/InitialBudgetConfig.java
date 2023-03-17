package com.bloxbean.cardano.aiken.tx.evaluator;

import com.sun.jna.Structure;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Closeable;
import java.io.IOException;

@AllArgsConstructor
@NoArgsConstructor
@Structure.FieldOrder({"mem", "cpu"})
public class InitialBudgetConfig extends Structure implements Closeable {

    public static class InitialBudgetByValue extends InitialBudgetConfig implements ByValue { }

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
