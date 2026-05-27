package com.FinancIA.api.service;

import com.FinancIA.api.domain.Transaction;
import com.FinancIA.api.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;

    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    public Optional<Transaction> getTransactionById(Long id) {
        return transactionRepository.findById(id);
    }

    public Transaction createTransaction(Transaction transaction) {
        return transactionRepository.save(transaction);
    }

    public Transaction updateTransaction(Long id, Transaction transaction) {
        return transactionRepository.findById(id).map(existing -> {
            existing.setDescription(transaction.getDescription());
            existing.setAmount(transaction.getAmount());
            existing.setType(transaction.getType());
            existing.setDate(transaction.getDate());
            existing.setCategory(transaction.getCategory());
            existing.setNotes(transaction.getNotes());
            return transactionRepository.save(existing);
        }).orElseThrow(() -> new RuntimeException("Transaction not found"));
    }

    public void deleteTransaction(Long id) {
        transactionRepository.deleteById(id);
    }
}
