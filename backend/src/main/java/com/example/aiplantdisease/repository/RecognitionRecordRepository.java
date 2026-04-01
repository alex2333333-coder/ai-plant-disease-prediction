package com.example.aiplantdisease.repository;

import com.example.aiplantdisease.entity.RecognitionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecognitionRecordRepository extends JpaRepository<RecognitionRecord, String> {
  List<RecognitionRecord> findTop20ByOrderByCreateTimeDesc();
}

