-- MySQL 8.0：识别记录表
CREATE TABLE IF NOT EXISTS recognition_record (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  crop_type VARCHAR(128) NOT NULL,
  disease_name VARCHAR(256) NOT NULL,
  hazard_level VARCHAR(64) NOT NULL,
  prevention_advice TEXT,
  image_key VARCHAR(500) NOT NULL,
  is_alert TINYINT(1) NOT NULL DEFAULT 0,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

