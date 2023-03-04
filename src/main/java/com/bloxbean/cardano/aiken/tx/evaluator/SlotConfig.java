package com.bloxbean.cardano.aiken.tx.evaluator;

import com.sun.jna.Structure;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Closeable;
import java.io.IOException;

@AllArgsConstructor
@NoArgsConstructor
@Structure.FieldOrder({"slot_length", "zero_slot", "zero_time"})
public class SlotConfig extends Structure implements Closeable {
    public static class SlotConfigByValue extends SlotConfig implements Structure.ByReference { }

    public int slot_length;
    public long zero_slot;
    public long zero_time;

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
