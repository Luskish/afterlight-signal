package org.rllabs.afterlight.echo;

public interface EchoInventory {
    boolean hasFreeSlot();

    boolean insert(EchoIdentity identity);
}
