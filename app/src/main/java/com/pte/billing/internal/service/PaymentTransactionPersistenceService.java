package com.pte.billing.internal.service;

import com.pte.billing.domain.PaymentTransaction;
import com.pte.billing.internal.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists webhook audit rows, including invalid requests that will be rejected. */
@Service
public class PaymentTransactionPersistenceService {

    private final PaymentTransactionRepository paymentTransactionRepository;

    public PaymentTransactionPersistenceService(PaymentTransactionRepository paymentTransactionRepository) {
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    @Transactional
    public PaymentTransaction record(PaymentTransaction transaction) {
        return paymentTransactionRepository.saveAndFlush(transaction);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentTransaction recordRejected(PaymentTransaction transaction) {
        return paymentTransactionRepository.saveAndFlush(transaction);
    }
}
