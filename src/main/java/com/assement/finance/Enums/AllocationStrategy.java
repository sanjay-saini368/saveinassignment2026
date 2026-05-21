package com.assement.finance.Enums;

/**
 * Defines the order of allocation across loan components.
 *
 * CIP = Charges → Interest → Principal (default)
 * IPC = Interest → Principal → Charges
 * PIC = Principal → Interest → Charges
 */
public enum AllocationStrategy {
    CIP,  // Charges → Interest → Principal
    IPC,  // Interest → Principal → Charges
    PIC   // Principal → Interest → Charges
}