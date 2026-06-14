-- MySQL dump 10.13  Distrib 8.0.31, for macos12 (arm64)
--
-- Host: localhost    Database: private_cloud_disk
-- ------------------------------------------------------
-- Server version	8.0.31

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `pcd_directory_closure_table`
--

DROP TABLE IF EXISTS `pcd_directory_closure_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_directory_closure_table` (
  `user_id` binary(16) NOT NULL COMMENT 'ÊâÄÂ±ûÁî®Êà∑ID',
  `ancestor_id` binary(16) NOT NULL COMMENT 'Á•ñÂÖàËäÇÁÇπID',
  `descendant_id` binary(16) NOT NULL COMMENT 'Âêé‰ª£ËäÇÁÇπID',
  `depth` int NOT NULL COMMENT 'Á•ñÂÖà‰∏éÂêé‰ª£ËäÇÁÇπÊ∑±Â∫¶',
  PRIMARY KEY (`ancestor_id`,`descendant_id`),
  UNIQUE KEY `uk_descendant` (`user_id`,`descendant_id`,`ancestor_id`),
  KEY `descendant_id` (`descendant_id`),
  KEY `idx_depth` (`depth`),
  CONSTRAINT `pcd_directory_closure_table_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_directory_closure_table_ibfk_2` FOREIGN KEY (`ancestor_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_directory_closure_table_ibfk_3` FOREIGN KEY (`descendant_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ÁõÆÂΩïÊ†ëÈó≠ÂåÖË°®';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_directory_closure_table`
--

LOCK TABLES `pcd_directory_closure_table` WRITE;
/*!40000 ALTER TABLE `pcd_directory_closure_table` DISABLE KEYS */;
INSERT INTO `pcd_directory_closure_table` VALUES (_binary 'UUUUUUUUUUUUUUUU',_binary '4 tG[A\ËΩb4£óv',_binary '4 tG[A\ËΩb4£óv',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 'qpK°wOé°πçee$\Ô',_binary 'qpK°wOé°πçee$\Ô',0),(_binary 'UUUUUUUUUUUUUUUU',_binary 'û<W\Í˛H\Ï¨\÷\‹\«\„◊Ö\ﬂ',_binary 'û<W\Í˛H\Ï¨\÷\‹\«\„◊Ö\ﬂ',0),(_binary '',_binary '™™™™™™™™™™™™™™™°',_binary '™™™™™™™™™™™™™™™°',0),(_binary '\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"',_binary '™™™™™™™™™™™™™™™¢',_binary '™™™™™™™™™™™™™™™¢',0),(_binary '3333333333333333',_binary '™™™™™™™™™™™™™™™£',_binary '™™™™™™™™™™™™™™™£',0),(_binary 'DDDDDDDDDDDDDDDD',_binary '™™™™™™™™™™™™™™™§',_binary '™™™™™™™™™™™™™™™§',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '™™™™™™™™™™™™™™™•',_binary '4 tG[A\ËΩb4£óv',1),(_binary 'UUUUUUUUUUUUUUUU',_binary '™™™™™™™™™™™™™™™•',_binary 'qpK°wOé°πçee$\Ô',1),(_binary 'UUUUUUUUUUUUUUUU',_binary '™™™™™™™™™™™™™™™•',_binary 'û<W\Í˛H\Ï¨\÷\‹\«\„◊Ö\ﬂ',1),(_binary 'UUUUUUUUUUUUUUUU',_binary '™™™™™™™™™™™™™™™•',_binary '™™™™™™™™™™™™™™™•',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '™™™™™™™™™™™™™™™•',_binary '\ÀBŸ¢e\“I\'ã¶\\\»vÇ',1),(_binary 'UUUUUUUUUUUUUUUU',_binary '™™™™™™™™™™™™™™™•',_binary '\’gRûFÀû#V\»\ÿB\Õ(',1),(_binary 'UUUUUUUUUUUUUUUU',_binary '\ÀBŸ¢e\“I\'ã¶\\\»vÇ',_binary '\ÀBŸ¢e\“I\'ã¶\\\»vÇ',0),(_binary 'UUUUUUUUUUUUUUUU',_binary '\’gRûFÀû#V\»\ÿB\Õ(',_binary '\’gRûFÀû#V\»\ÿB\Õ(',0);
/*!40000 ALTER TABLE `pcd_directory_closure_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_directory_tree_table`
--

DROP TABLE IF EXISTS `pcd_directory_tree_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_directory_tree_table` (
  `node_id` binary(16) NOT NULL,
  `node_user_id` binary(16) NOT NULL COMMENT 'ÊâÄÂ±ûÁî®Êà∑ID',
  `node_parent_id` binary(16) DEFAULT NULL COMMENT 'Áà∂ËäÇÁÇπID',
  `node_name` varchar(200) NOT NULL COMMENT 'ËäÇÁÇπÂêçÁß∞',
  `node_create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'ËäÇÁÇπÂàõÂª∫Êó∂Èó¥',
  `node_status` enum('lock','active','pending','trashed','deleted') DEFAULT 'active' COMMENT 'ËäÇÁÇπÁä∂ÊÄÅ',
  PRIMARY KEY (`node_id`),
  UNIQUE KEY `uk_directory_tree` (`node_id`,`node_user_id`,`node_parent_id`),
  KEY `node_user_id` (`node_user_id`),
  KEY `node_parent_id` (`node_parent_id`),
  CONSTRAINT `pcd_directory_tree_table_ibfk_1` FOREIGN KEY (`node_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_directory_tree_table_ibfk_2` FOREIGN KEY (`node_parent_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ËäÇÁÇπÁõÆÂΩïÊ†ëË°®';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_directory_tree_table`
--

LOCK TABLES `pcd_directory_tree_table` WRITE;
/*!40000 ALTER TABLE `pcd_directory_tree_table` DISABLE KEYS */;
INSERT INTO `pcd_directory_tree_table` VALUES (_binary '4 tG[A\ËΩb4£óv',_binary 'UUUUUUUUUUUUUUUU',_binary '™™™™™™™™™™™™™™™•','test','2026-06-14 09:57:10','active'),(_binary 'qpK°wOé°πçee$\Ô',_binary 'UUUUUUUUUUUUUUUU',_binary '™™™™™™™™™™™™™™™•','9','2026-06-13 17:09:14','deleted'),(_binary 'û<W\Í˛H\Ï¨\÷\‹\«\„◊Ö\ﬂ',_binary 'UUUUUUUUUUUUUUUU',_binary '™™™™™™™™™™™™™™™•','data','2026-06-14 09:57:27','deleted'),(_binary '™™™™™™™™™™™™™™™°',_binary '',NULL,'root','2026-06-10 14:52:38','active'),(_binary '™™™™™™™™™™™™™™™¢',_binary '\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"',NULL,'root','2026-06-10 14:52:38','active'),(_binary '™™™™™™™™™™™™™™™£',_binary '3333333333333333',NULL,'root','2026-06-10 14:52:38','active'),(_binary '™™™™™™™™™™™™™™™§',_binary 'DDDDDDDDDDDDDDDD',NULL,'root','2026-06-10 14:52:38','active'),(_binary '™™™™™™™™™™™™™™™•',_binary 'UUUUUUUUUUUUUUUU',NULL,'root','2026-06-10 14:52:38','active'),(_binary '\ÀBŸ¢e\“I\'ã¶\\\»vÇ',_binary 'UUUUUUUUUUUUUUUU',_binary '™™™™™™™™™™™™™™™•','hello','2026-06-14 09:57:15','active'),(_binary '\’gRûFÀû#V\»\ÿB\Õ(',_binary 'UUUUUUUUUUUUUUUU',_binary '™™™™™™™™™™™™™™™•','111111111','2026-06-14 09:57:19','active');
/*!40000 ALTER TABLE `pcd_directory_tree_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_file_info_table`
--

DROP TABLE IF EXISTS `pcd_file_info_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_file_info_table` (
  `file_name` varchar(150) NOT NULL COMMENT 'Êñá‰ª∂ÂêçÁß∞',
  `file_uploaded_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Êñá‰ª∂‰∏ä‰º†Êó∂Èó¥',
  `file_size` bigint NOT NULL COMMENT 'Êñá‰ª∂Â§ßÂ∞è',
  `file_type` varchar(120) NOT NULL COMMENT 'Êñá‰ª∂Á±ªÂûã',
  `file_author_id` binary(16) NOT NULL COMMENT 'Êñá‰ª∂‰ΩúËÄÖID',
  `file_id` binary(16) NOT NULL,
  `file_checksum` varchar(256) NOT NULL COMMENT 'Êñá‰ª∂Ê†°È™åÂÄº',
  `file_total_chunks` int NOT NULL COMMENT 'Êñá‰ª∂ÂàáÁâáÊï∞ÁõÆ',
  `file_node_id` binary(16) NOT NULL COMMENT 'Êñá‰ª∂ÊâÄÂú®ÁõÆÂΩïËäÇÁÇπID',
  `file_storage_path` varchar(512) DEFAULT NULL COMMENT 'Êñá‰ª∂Â≠òÂÇ®Ë∑ØÂæÑ',
  `file_status` enum('active','deleted','trashed','merging','merged','merge_failed','scanning','scan_failed','reject') NOT NULL DEFAULT 'active' COMMENT 'Êñá‰ª∂Áä∂ÊÄÅ',
  PRIMARY KEY (`file_id`),
  UNIQUE KEY `uk_file_info` (`file_id`,`file_author_id`,`file_node_id`),
  KEY `file_author_id` (`file_author_id`),
  KEY `fk_file_info_directory_tree` (`file_node_id`),
  CONSTRAINT `fk_file_info_directory_tree` FOREIGN KEY (`file_node_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_file_info_table_ibfk_1` FOREIGN KEY (`file_author_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Êñá‰ª∂‰ø°ÊÅØË°®';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_file_info_table`
--

LOCK TABLES `pcd_file_info_table` WRITE;
/*!40000 ALTER TABLE `pcd_file_info_table` DISABLE KEYS */;
INSERT INTO `pcd_file_info_table` VALUES ('EXCELÊïôÁ®ã | ÊãúÊâò‰∏âËøû‰∫ÜÔºÅÂÖ®BÁ´ôÊúÄÁî®ÂøÉÔºàÊ≤°Êúâ‰πã‰∏ÄÔºâÁöÑEXCELÂÖçË¥πËØæÁ®ãÔºÅOFFICE-WPS-Ë°®Ê†º-EXCELÂáΩÊï∞-EXCELÊäÄÂ∑ß-Êï∞ÊçÆÂàÜÊûê-ÂäûÂÖ¨ËΩØ‰ª∂ - 004 - S03-Âø´ÈÄüÂ°´ÂÖÖÔºåÊâπÈáèÊèêÂèñ‰∏éÁªÑÂêàÊï∞ÊçÆÁöÑÁ•ûÂ•á.mp4','2026-06-14 18:44:17',33758169,'video/mp4',_binary 'UUUUUUUUUUUUUUUU',_binary '\‚¸$\‹Aê•v\Ú#Gù\Ó','e459ac11a97001e117b60c5b65e7a8e491976a17282ab7680f909f1a3105fae0',7,_binary '™™™™™™™™™™™™™™™•','../Uploads/storage/64f6c940-4b3b-4cc9-aabf-af9225e0de93-7.cloud','active'),('connect.py','2026-06-14 18:22:32',1536,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary '\"2[\'©éFßí‚ã§Äu\„','8b0f6bd56051930131e764b70e501cbcaeb6c36bc1afc975c6ec386169113009',1,_binary '™™™™™™™™™™™™™™™•',NULL,'merging'),('java_error_in_studio.hprof','2026-06-14 08:59:04',383744933,'application/octet-stream',_binary 'UUUUUUUUUUUUUUUU',_binary 'Pñ\Õ\÷\„*@\0æÆt\r\‰\‹y','6b47537f8e733eb2bacd880a43c378216bf9753e699dae150de12ce27d9eea0f',74,_binary '™™™™™™™™™™™™™™™•',NULL,'merging'),('Java‰∫åÁ∫ßÂ§ßÁ∫≤.pdf','2026-06-13 19:38:18',120547,'application/pdf',_binary 'UUUUUUUUUUUUUUUU',_binary 'r	/é }Kãßí≠;©','3ea82b01db7948e60f471ae6ac23beb6ea659b69077a35a91689c7dd306606d3',1,_binary '™™™™™™™™™™™™™™™•','../Uploads/storage/0b71e23c-e14b-4ee8-93d7-c218fbb73be1-1.cloud','deleted'),('tests7.py','2026-06-13 19:05:22',4638,'text/x-python-script',_binary 'UUUUUUUUUUUUUUUU',_binary 'tá=œé\ÃI«Øæ;c\’g','aeab8ac72fbe88e484ab1a921352fb0cf6b074ee57b5b5826fe3254daf1e9c13',1,_binary '™™™™™™™™™™™™™™™•','../Uploads/storage/da9f4f0d-4837-4075-886c-247253dca1c6-1.cloud','deleted'),('client-download-2.html','2026-06-14 18:44:44',48100,'text/html',_binary 'UUUUUUUUUUUUUUUU',_binary 'yµ\ı@K.DIö)\Âm|\Z','3e92f6fdf5c8da98173517c439650673dbfec19a7254904422e1a96c6f7a8005',1,_binary '™™™™™™™™™™™™™™™•','../Uploads/storage/80fb0d51-b7bc-4305-bab9-649d88e282ae-1.cloud','active'),('„ÄäÂÆûÊó∂Á¢∞ÊíûÊ£ÄÊµã„Äã.pptx','2026-06-14 18:43:30',42317380,'application/vnd.openxmlformats-officedocument.presentationml.presentation',_binary 'UUUUUUUUUUUUUUUU',_binary 'âG0◊•\≈OEâ∫\‹\¬0\Òæ\ﬁ','70e11359955d3d830c7a42d90455a75870593ed90f2d045147f09d092825171d',9,_binary '™™™™™™™™™™™™™™™•','../Uploads/storage/15891069-f205-423f-9bd9-a316527a481d-9.cloud','active'),('REST API basics- CRUD, test & variable.postman_collection.json','2026-06-13 19:37:59',21243,'application/json',_binary 'UUUUUUUUUUUUUUUU',_binary '∞o\˜¶f_DáÑ\r*/§%\'p','6265c07867f05e56e4b44e95de27ceb376b7b918c2a0dd31fe21ce78e567286d',1,_binary '™™™™™™™™™™™™™™™•','../Uploads/storage/8628811f-8e55-4a04-a861-bc769ae860a9-1.cloud','deleted'),('ÁßÅÊúâ‰∫ëÁõòÁ≥ªÁªü - ÂÆåÊï¥APIÊµãËØï-documentation.html','2026-06-14 05:21:00',39838,'text/html',_binary 'UUUUUUUUUUUUUUUU',_binary '≥b\ÂêFI⁄π<°\ÿ@!','f4cfaace78a88f6911833f1f41cf20b1b46e56006d2003d8dc2797420049db3d',1,_binary '™™™™™™™™™™™™™™™•','../Uploads/storage/376947d9-3cb3-47a7-b76d-89f3f020f9a2-1.cloud','deleted'),('DB368C5971D4DD40BDFDE00C39154A84.jpg','2026-06-14 18:42:48',820229,'image/jpeg',_binary 'UUUUUUUUUUUUUUUU',_binary '\ÁE\Ú4S∏Fíµñ)B\€\ÈIk','f62761e04a2f678599c297f69a9a189b31fa1d27989e64b863ba10196b39ec3b',1,_binary '™™™™™™™™™™™™™™™•','../Uploads/storage/ccee1b9c-9111-4583-bde5-7573597ff148-1.cloud','active');
/*!40000 ALTER TABLE `pcd_file_info_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_file_star_table`
--

DROP TABLE IF EXISTS `pcd_file_star_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_file_star_table` (
  `star_id` bigint NOT NULL AUTO_INCREMENT,
  `star_user_id` binary(16) NOT NULL COMMENT 'Áî®Êà∑ID',
  `star_file_id` binary(16) NOT NULL COMMENT 'Êñá‰ª∂ID',
  `star_starred_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Êî∂ËóèÊó∂Èó¥',
  PRIMARY KEY (`star_id`),
  UNIQUE KEY `uk_user_file` (`star_user_id`,`star_file_id`),
  KEY `star_file_id` (`star_file_id`),
  KEY `idx_user_starred` (`star_user_id`,`star_starred_at`),
  CONSTRAINT `pcd_file_star_table_ibfk_1` FOREIGN KEY (`star_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_file_star_table_ibfk_2` FOREIGN KEY (`star_file_id`) REFERENCES `pcd_file_info_table` (`file_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Êñá‰ª∂Êî∂ËóèË°®';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_file_star_table`
--

LOCK TABLES `pcd_file_star_table` WRITE;
/*!40000 ALTER TABLE `pcd_file_star_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_file_star_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_login_audit_table`
--

DROP TABLE IF EXISTS `pcd_login_audit_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_login_audit_table` (
  `audit_id` bigint NOT NULL AUTO_INCREMENT,
  `audit_user_id` binary(16) DEFAULT NULL COMMENT 'ÂåπÈÖçÂà∞ÁöÑÁî®Êà∑ID',
  `audit_account` varchar(100) DEFAULT NULL COMMENT 'ÁôªÂΩïË¥¶Âè∑',
  `audit_phone_number` varchar(50) DEFAULT NULL COMMENT 'ÁôªÂΩïÊâãÊú∫Âè∑',
  `audit_success` tinyint(1) NOT NULL COMMENT 'ÊòØÂê¶ÁôªÂΩïÊàêÂäü',
  `audit_failure_reason` varchar(120) DEFAULT NULL COMMENT 'Â§±Ë¥•ÂéüÂõ†',
  `audit_client_ip` varchar(64) DEFAULT NULL COMMENT 'ÂÆ¢Êà∑Á´ØIP',
  `audit_user_agent` varchar(512) DEFAULT NULL COMMENT 'User-Agent',
  `audit_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`audit_id`),
  KEY `idx_login_audit_user_time` (`audit_user_id`,`audit_created_at`),
  KEY `idx_login_audit_account_time` (`audit_account`,`audit_created_at`),
  KEY `idx_login_audit_ip_time` (`audit_client_ip`,`audit_created_at`),
  CONSTRAINT `pcd_login_audit_table_ibfk_1` FOREIGN KEY (`audit_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ÁôªÂΩïÂÆ°ËÆ°Ë°®';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_login_audit_table`
--

LOCK TABLES `pcd_login_audit_table` WRITE;
/*!40000 ALTER TABLE `pcd_login_audit_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_login_audit_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_login_session_table`
--

DROP TABLE IF EXISTS `pcd_login_session_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_login_session_table` (
  `login_session_id` binary(16) NOT NULL COMMENT '‰ºöËØùID',
  `login_session_user_id` binary(16) NOT NULL COMMENT 'ÁôªÂΩïÁî®Êà∑ID',
  `login_session_device_id` binary(16) DEFAULT NULL COMMENT 'ÂÖ≥ËÅîËÆæÂ§áID',
  `login_session_token_jti` binary(16) DEFAULT NULL COMMENT 'JWT jti',
  `login_session_client_ip` varchar(64) DEFAULT NULL COMMENT 'ÁôªÂΩïIP',
  `login_session_user_agent` varchar(512) DEFAULT NULL COMMENT 'User-Agent',
  `login_session_started_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `login_session_expires_at` datetime NOT NULL COMMENT '‰ºöËØùËøáÊúüÊó∂Èó¥',
  `login_session_revoked_at` datetime DEFAULT NULL COMMENT '‰ºöËØùÊí§ÈîÄÊó∂Èó¥',
  `login_session_status` enum('active','expired','revoked') NOT NULL DEFAULT 'active',
  PRIMARY KEY (`login_session_id`),
  KEY `idx_login_session_user_status` (`login_session_user_id`,`login_session_status`),
  KEY `idx_login_session_device_status` (`login_session_device_id`,`login_session_status`),
  KEY `idx_login_session_jti` (`login_session_token_jti`),
  CONSTRAINT `pcd_login_session_table_ibfk_1` FOREIGN KEY (`login_session_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_login_session_table_ibfk_2` FOREIGN KEY (`login_session_device_id`) REFERENCES `pcd_user_device_table` (`device_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Áî®Êà∑ÁôªÂΩï‰ºöËØùË°®';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_login_session_table`
--

LOCK TABLES `pcd_login_session_table` WRITE;
/*!40000 ALTER TABLE `pcd_login_session_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_login_session_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_notification_send_log_table`
--

DROP TABLE IF EXISTS `pcd_notification_send_log_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_notification_send_log_table` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '‰∏ªÈîÆËá™Â¢ûID',
  `event_id` varchar(255) NOT NULL COMMENT '‰∫ã‰ª∂ÂîØ‰∏ÄID',
  `channel` varchar(20) NOT NULL COMMENT 'ÈÄöÈÅì',
  `receiver` varchar(255) NOT NULL COMMENT 'Êé•Êî∂ËÄÖ',
  `user_id` binary(16) DEFAULT NULL COMMENT 'ÂÖ≥ËÅîÁî®Êà∑ID',
  `status` varchar(20) NOT NULL COMMENT 'Áä∂ÊÄÅ',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT 'ÈáçËØïÊ¨°Êï∞',
  `error_message` varchar(1000) DEFAULT NULL COMMENT 'ÈîôËØØ‰ø°ÊÅØ',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'ÂàõÂª∫Êó∂Èó¥',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Êõ¥Êñ∞Êó∂Èó¥',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_channel_receiver` (`event_id`,`channel`,`receiver`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ÈÄöÁü•ÂèëÈÄÅÊó•ÂøóË°®';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_notification_send_log_table`
--

LOCK TABLES `pcd_notification_send_log_table` WRITE;
/*!40000 ALTER TABLE `pcd_notification_send_log_table` DISABLE KEYS */;
INSERT INTO `pcd_notification_send_log_table` VALUES (1,'email-verify:hellomwz@outlook.com:1781265551034','EMAIL','hellomwz@outlook.com',NULL,'SUCCESS',0,NULL,'2026-06-12 19:59:14','2026-06-12 19:59:20'),(2,'email-verify:hellomwz@outlook.com:1781265722479','EMAIL','hellomwz@outlook.com',NULL,'SUCCESS',0,NULL,'2026-06-12 20:02:03','2026-06-12 20:02:06');
/*!40000 ALTER TABLE `pcd_notification_send_log_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_sharing_Link_mange_table`
--

DROP TABLE IF EXISTS `pcd_sharing_Link_mange_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_sharing_Link_mange_table` (
  `sharing_link_id` binary(16) NOT NULL,
  `sharing_link_path` varchar(512) NOT NULL COMMENT 'ÂàÜ‰∫´ÈìæÊé•Ë∑ØÂæÑ',
  `sharing_link_file_id` binary(16) NOT NULL COMMENT 'ÂàÜ‰∫´ÈìæÊé•ÂÖ≥ËÅîÁöÑÊñá‰ª∂ID',
  `sharing_link_valid_starting_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'ÂàÜ‰∫´ÈìæÊé•ÊúâÊïàÂºÄÂßãÊó∂Èó¥',
  `sharing_link_valid_endding_time` timestamp NOT NULL COMMENT 'ÂàÜ‰∫´ÈìæÊé•ÊúâÊïàÁªìÊùüÊó∂Èó¥',
  `sharing_link_password` varchar(60) DEFAULT NULL COMMENT 'ÂàÜ‰∫´ÈìæÊé•ÂØÜÁ†Å',
  PRIMARY KEY (`sharing_link_id`),
  KEY `sharing_link_file_id` (`sharing_link_file_id`),
  CONSTRAINT `pcd_sharing_link_mange_table_ibfk_1` FOREIGN KEY (`sharing_link_file_id`) REFERENCES `pcd_file_info_table` (`file_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Êñá‰ª∂ÂàÜ‰∫´ÈìæÊé•ÁÆ°ÁêÜË°®';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_sharing_Link_mange_table`
--

LOCK TABLES `pcd_sharing_Link_mange_table` WRITE;
/*!40000 ALTER TABLE `pcd_sharing_Link_mange_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_sharing_Link_mange_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_trash_target_table`
--

DROP TABLE IF EXISTS `pcd_trash_target_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_trash_target_table` (
  `trash_id` bigint NOT NULL AUTO_INCREMENT,
  `trash_target_id` binary(16) NOT NULL COMMENT 'ÂéüÊñá‰ª∂ID',
  `trash_target_type` enum('file','folder') NOT NULL COMMENT 'ÁõÆÊ†áÁ±ªÂûã',
  `trash_user_id` binary(16) NOT NULL COMMENT 'Áî®Êà∑ID',
  `trash_target_name` varchar(150) NOT NULL COMMENT 'Êñá‰ª∂ÂêçÁß∞',
  `trash_file_type` varchar(120) DEFAULT NULL COMMENT 'Êñá‰ª∂Á±ªÂûã',
  `trash_target_size` bigint DEFAULT NULL COMMENT 'Êñá‰ª∂Â§ßÂ∞è',
  `trash_original_node_id` binary(16) NOT NULL COMMENT 'ÂéüËäÇÁÇπID',
  `trash_deleted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Âà†Èô§Êó∂Èó¥',
  `trash_expires_at` datetime NOT NULL COMMENT 'ËøáÊúüÊó∂Èó¥',
  PRIMARY KEY (`trash_id`),
  KEY `idx_user_deleted` (`trash_user_id`,`trash_deleted_at`),
  KEY `idx_expires` (`trash_expires_at`),
  KEY `pcd_trash_target_table_trash_target_id_trash_target_type_index` (`trash_target_id`,`trash_target_type`),
  CONSTRAINT `pcd_trash_target_table_ibfk_1` FOREIGN KEY (`trash_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=115 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ÂõûÊî∂Á´ôÊñá‰ª∂Ë°®';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_trash_target_table`
--

LOCK TABLES `pcd_trash_target_table` WRITE;
/*!40000 ALTER TABLE `pcd_trash_target_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_trash_target_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_upload_chunks_table`
--

DROP TABLE IF EXISTS `pcd_upload_chunks_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_upload_chunks_table` (
  `chunk_uploads_id` binary(16) NOT NULL COMMENT 'ÂÖ≥ËÅî‰∏ä‰º†‰ºöËØùID',
  `chunk_index` int NOT NULL COMMENT 'ÂàáÁâáÁ¥¢Âºï',
  `chunk_status` enum('pending','uploading','uploaded','failed') DEFAULT 'pending' COMMENT 'ÂàáÁâáÁä∂ÊÄÅ',
  `chunk_storage_path` varchar(512) NOT NULL COMMENT 'ÂàáÁâáÂ≠òÂÇ®Ë∑ØÂæÑ',
  `chunk_uploaded_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'ÂàáÁâá‰∏ä‰º†Êó∂Èó¥',
  PRIMARY KEY (`chunk_uploads_id`,`chunk_index`),
  CONSTRAINT `pcd_upload_chunks_table_ibfk_1` FOREIGN KEY (`chunk_uploads_id`) REFERENCES `pcd_uploads_session_table` (`uploads_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Êñá‰ª∂ÂàáÁâáË°®';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_upload_chunks_table`
--

LOCK TABLES `pcd_upload_chunks_table` WRITE;
/*!40000 ALTER TABLE `pcd_upload_chunks_table` DISABLE KEYS */;
INSERT INTO `pcd_upload_chunks_table` VALUES (_binary '\r\»z\Ô≤0GÀø«Ä\Î\À<Z',1,'uploaded','../Uploads/0dc87aef-b230-47cb-bfc7-8014ebcb3c5a-1.part','2026-06-13 18:39:10'),(_binary 'øOèÑxI£§K≠ü\€\Ó',1,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-1.part','2026-06-14 08:58:55'),(_binary 'øOèÑxI£§K≠ü\€\Ó',2,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-2.part','2026-06-14 08:58:55'),(_binary 'øOèÑxI£§K≠ü\€\Ó',3,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-3.part','2026-06-14 08:58:55'),(_binary 'øOèÑxI£§K≠ü\€\Ó',4,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-4.part','2026-06-14 08:58:56'),(_binary 'øOèÑxI£§K≠ü\€\Ó',5,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-5.part','2026-06-14 08:58:56'),(_binary 'øOèÑxI£§K≠ü\€\Ó',6,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-6.part','2026-06-14 08:58:56'),(_binary 'øOèÑxI£§K≠ü\€\Ó',7,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-7.part','2026-06-14 08:58:56'),(_binary 'øOèÑxI£§K≠ü\€\Ó',8,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-8.part','2026-06-14 08:58:56'),(_binary 'øOèÑxI£§K≠ü\€\Ó',9,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-9.part','2026-06-14 08:58:56'),(_binary 'øOèÑxI£§K≠ü\€\Ó',10,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-10.part','2026-06-14 08:58:56'),(_binary 'øOèÑxI£§K≠ü\€\Ó',11,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-11.part','2026-06-14 08:58:56'),(_binary 'øOèÑxI£§K≠ü\€\Ó',12,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-12.part','2026-06-14 08:58:56'),(_binary 'øOèÑxI£§K≠ü\€\Ó',13,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-13.part','2026-06-14 08:58:57'),(_binary 'øOèÑxI£§K≠ü\€\Ó',14,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-14.part','2026-06-14 08:58:56'),(_binary 'øOèÑxI£§K≠ü\€\Ó',15,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-15.part','2026-06-14 08:58:57'),(_binary 'øOèÑxI£§K≠ü\€\Ó',16,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-16.part','2026-06-14 08:58:57'),(_binary 'øOèÑxI£§K≠ü\€\Ó',17,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-17.part','2026-06-14 08:58:57'),(_binary 'øOèÑxI£§K≠ü\€\Ó',18,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-18.part','2026-06-14 08:58:57'),(_binary 'øOèÑxI£§K≠ü\€\Ó',19,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-19.part','2026-06-14 08:58:57'),(_binary 'øOèÑxI£§K≠ü\€\Ó',20,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-20.part','2026-06-14 08:58:57'),(_binary 'øOèÑxI£§K≠ü\€\Ó',21,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-21.part','2026-06-14 08:58:57'),(_binary 'øOèÑxI£§K≠ü\€\Ó',22,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-22.part','2026-06-14 08:58:57'),(_binary 'øOèÑxI£§K≠ü\€\Ó',23,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-23.part','2026-06-14 08:58:57'),(_binary 'øOèÑxI£§K≠ü\€\Ó',24,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-24.part','2026-06-14 08:58:57'),(_binary 'øOèÑxI£§K≠ü\€\Ó',25,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-25.part','2026-06-14 08:58:58'),(_binary 'øOèÑxI£§K≠ü\€\Ó',26,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-26.part','2026-06-14 08:58:58'),(_binary 'øOèÑxI£§K≠ü\€\Ó',27,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-27.part','2026-06-14 08:58:58'),(_binary 'øOèÑxI£§K≠ü\€\Ó',28,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-28.part','2026-06-14 08:58:58'),(_binary 'øOèÑxI£§K≠ü\€\Ó',29,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-29.part','2026-06-14 08:58:58'),(_binary 'øOèÑxI£§K≠ü\€\Ó',30,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-30.part','2026-06-14 08:58:58'),(_binary 'øOèÑxI£§K≠ü\€\Ó',31,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-31.part','2026-06-14 08:58:58'),(_binary 'øOèÑxI£§K≠ü\€\Ó',32,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-32.part','2026-06-14 08:58:58'),(_binary 'øOèÑxI£§K≠ü\€\Ó',33,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-33.part','2026-06-14 08:58:58'),(_binary 'øOèÑxI£§K≠ü\€\Ó',34,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-34.part','2026-06-14 08:58:58'),(_binary 'øOèÑxI£§K≠ü\€\Ó',35,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-35.part','2026-06-14 08:58:58'),(_binary 'øOèÑxI£§K≠ü\€\Ó',36,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-36.part','2026-06-14 08:58:58'),(_binary 'øOèÑxI£§K≠ü\€\Ó',37,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-37.part','2026-06-14 08:58:59'),(_binary 'øOèÑxI£§K≠ü\€\Ó',38,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-38.part','2026-06-14 08:58:59'),(_binary 'øOèÑxI£§K≠ü\€\Ó',39,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-39.part','2026-06-14 08:58:59'),(_binary 'øOèÑxI£§K≠ü\€\Ó',40,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-40.part','2026-06-14 08:58:59'),(_binary 'øOèÑxI£§K≠ü\€\Ó',41,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-41.part','2026-06-14 08:58:59'),(_binary 'øOèÑxI£§K≠ü\€\Ó',42,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-42.part','2026-06-14 08:58:59'),(_binary 'øOèÑxI£§K≠ü\€\Ó',43,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-43.part','2026-06-14 08:59:00'),(_binary 'øOèÑxI£§K≠ü\€\Ó',44,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-44.part','2026-06-14 08:59:00'),(_binary 'øOèÑxI£§K≠ü\€\Ó',45,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-45.part','2026-06-14 08:59:00'),(_binary 'øOèÑxI£§K≠ü\€\Ó',46,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-46.part','2026-06-14 08:59:00'),(_binary 'øOèÑxI£§K≠ü\€\Ó',47,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-47.part','2026-06-14 08:59:00'),(_binary 'øOèÑxI£§K≠ü\€\Ó',48,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-48.part','2026-06-14 08:59:00'),(_binary 'øOèÑxI£§K≠ü\€\Ó',49,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-49.part','2026-06-14 08:59:00'),(_binary 'øOèÑxI£§K≠ü\€\Ó',50,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-50.part','2026-06-14 08:59:00'),(_binary 'øOèÑxI£§K≠ü\€\Ó',51,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-51.part','2026-06-14 08:59:00'),(_binary 'øOèÑxI£§K≠ü\€\Ó',52,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-52.part','2026-06-14 08:59:01'),(_binary 'øOèÑxI£§K≠ü\€\Ó',53,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-53.part','2026-06-14 08:59:01'),(_binary 'øOèÑxI£§K≠ü\€\Ó',54,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-54.part','2026-06-14 08:59:01'),(_binary 'øOèÑxI£§K≠ü\€\Ó',55,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-55.part','2026-06-14 08:59:01'),(_binary 'øOèÑxI£§K≠ü\€\Ó',56,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-56.part','2026-06-14 08:59:01'),(_binary 'øOèÑxI£§K≠ü\€\Ó',57,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-57.part','2026-06-14 08:59:01'),(_binary 'øOèÑxI£§K≠ü\€\Ó',58,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-58.part','2026-06-14 08:59:01'),(_binary 'øOèÑxI£§K≠ü\€\Ó',59,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-59.part','2026-06-14 08:59:01'),(_binary 'øOèÑxI£§K≠ü\€\Ó',60,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-60.part','2026-06-14 08:59:02'),(_binary 'øOèÑxI£§K≠ü\€\Ó',61,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-61.part','2026-06-14 08:59:02'),(_binary 'øOèÑxI£§K≠ü\€\Ó',62,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-62.part','2026-06-14 08:59:02'),(_binary 'øOèÑxI£§K≠ü\€\Ó',63,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-63.part','2026-06-14 08:59:02'),(_binary 'øOèÑxI£§K≠ü\€\Ó',64,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-64.part','2026-06-14 08:59:02'),(_binary 'øOèÑxI£§K≠ü\€\Ó',65,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-65.part','2026-06-14 08:59:02'),(_binary 'øOèÑxI£§K≠ü\€\Ó',66,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-66.part','2026-06-14 08:59:02'),(_binary 'øOèÑxI£§K≠ü\€\Ó',67,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-67.part','2026-06-14 08:59:02'),(_binary 'øOèÑxI£§K≠ü\€\Ó',68,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-68.part','2026-06-14 08:59:02'),(_binary 'øOèÑxI£§K≠ü\€\Ó',69,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-69.part','2026-06-14 08:59:02'),(_binary 'øOèÑxI£§K≠ü\€\Ó',70,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-70.part','2026-06-14 08:59:03'),(_binary 'øOèÑxI£§K≠ü\€\Ó',71,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-71.part','2026-06-14 08:59:03'),(_binary 'øOèÑxI£§K≠ü\€\Ó',72,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-72.part','2026-06-14 08:59:03'),(_binary 'øOèÑxI£§K≠ü\€\Ó',73,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-73.part','2026-06-14 08:59:04'),(_binary 'øOèÑxI£§K≠ü\€\Ó',74,'uploaded','../Uploads/bf4f018f-8478-49a3-a407-4bad049fdbee-74.part','2026-06-14 08:59:04'),(_binary '\Ÿˇß\»wÆE¨ñL`/´º',1,'uploaded','../Uploads/d9ffa7c8-77ae-45ac-9602-4c607f2fabbc-1.part','2026-06-14 18:22:32');
/*!40000 ALTER TABLE `pcd_upload_chunks_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_uploads_session_table`
--

DROP TABLE IF EXISTS `pcd_uploads_session_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_uploads_session_table` (
  `uploads_id` binary(16) NOT NULL,
  `uploads_user_id` binary(16) NOT NULL COMMENT '‰∏ä‰º†Áî®Êà∑ID',
  `uploads_total_chunks` int NOT NULL COMMENT '‰∏ä‰º†ÂàáÁâáÊÄªÊï∞',
  `uploads_starting_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '‰∏ä‰º†ÂºÄÂßãÊó∂Èó¥',
  `uploads_endding_time` timestamp NOT NULL COMMENT '‰∏ä‰º†ÁªìÊùüÊó∂Èó¥',
  `uploads_file_size` bigint NOT NULL COMMENT 'Êñá‰ª∂Â§ßÂ∞è',
  `uploads_file_checksum` varchar(256) NOT NULL COMMENT 'Êñá‰ª∂Ê†°È™åÂÄº',
  `uploads_chunks_max_size` int NOT NULL COMMENT 'ÂàáÁâáÊúÄÂ§ßÂ§ßÂ∞è',
  `uploads_file_name` varchar(150) NOT NULL COMMENT 'Êñá‰ª∂ÂêçÁß∞',
  `uploads_file_type` varchar(120) NOT NULL COMMENT 'Êñá‰ª∂Á±ªÂûã',
  `uploads_node_id` binary(16) NOT NULL COMMENT 'Êñá‰ª∂ÊâÄÂú®ÁõÆÂΩïËäÇÁÇπID',
  `uploads_status` enum('uploading','merging','completed','failed','cancel') DEFAULT 'uploading' COMMENT '‰∏ä‰º†Áä∂ÊÄÅ',
  PRIMARY KEY (`uploads_id`),
  KEY `uploads_user_id` (`uploads_user_id`),
  KEY `fk_uploads_session_directory_tree` (`uploads_node_id`),
  CONSTRAINT `fk_uploads_session_directory_tree` FOREIGN KEY (`uploads_node_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_uploads_session_table_ibfk_1` FOREIGN KEY (`uploads_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Êñá‰ª∂‰∏ä‰º†‰ºöËØùË°®';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_uploads_session_table`
--

LOCK TABLES `pcd_uploads_session_table` WRITE;
/*!40000 ALTER TABLE `pcd_uploads_session_table` DISABLE KEYS */;
INSERT INTO `pcd_uploads_session_table` VALUES (_binary '\r\»z\Ô≤0GÀø«Ä\Î\À<Z',_binary 'UUUUUUUUUUUUUUUU',1,'2026-06-13 18:39:08','2026-06-13 19:09:08',39962,'9b35266211d0ab20f5ac865472f43d57bf782fe0f90f7cb28de9ad7e78e89171',5242880,'index-2.html','text/html',_binary '™™™™™™™™™™™™™™™•','merging'),(_binary 'øOèÑxI£§K≠ü\€\Ó',_binary 'UUUUUUUUUUUUUUUU',74,'2026-06-14 08:58:54','2026-06-14 09:28:54',383744933,'6b47537f8e733eb2bacd880a43c378216bf9753e699dae150de12ce27d9eea0f',5242880,'java_error_in_studio.hprof','application/octet-stream',_binary '™™™™™™™™™™™™™™™•','merging'),(_binary '\Ÿˇß\»wÆE¨ñL`/´º',_binary 'UUUUUUUUUUUUUUUU',1,'2026-06-14 18:22:31','2026-06-14 18:52:31',1536,'8b0f6bd56051930131e764b70e501cbcaeb6c36bc1afc975c6ec386169113009',5242880,'connect.py','text/x-python-script',_binary '™™™™™™™™™™™™™™™•','merging');
/*!40000 ALTER TABLE `pcd_uploads_session_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_user_device_table`
--

DROP TABLE IF EXISTS `pcd_user_device_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_user_device_table` (
  `device_id` binary(16) NOT NULL COMMENT 'ÊúçÂä°Á´ØÁîüÊàêÁöÑËÆæÂ§áID',
  `device_user_id` binary(16) NOT NULL COMMENT 'ÊâÄÂ±ûÁî®Êà∑ID',
  `device_client_type` varchar(50) NOT NULL COMMENT 'ÂÆ¢Êà∑Á´ØÁ±ªÂûã',
  `device_client_name` varchar(120) DEFAULT NULL COMMENT 'ÂÆ¢Êà∑Á´ØÂ±ïÁ§∫ÂêçÁß∞',
  `device_platform` varchar(120) DEFAULT NULL COMMENT 'Á≥ªÁªüÊàñÂπ≥Âè∞‰ø°ÊÅØ',
  `device_user_agent_hash` varchar(64) DEFAULT NULL COMMENT 'User-AgentÂìàÂ∏å',
  `device_public_key` text COMMENT 'ËÆæÂ§áÂÖ¨Èí•',
  `device_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `device_last_seen_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `device_status` enum('active','disabled','revoked') NOT NULL DEFAULT 'active',
  PRIMARY KEY (`device_id`),
  KEY `idx_device_user_status` (`device_user_id`,`device_status`),
  CONSTRAINT `pcd_user_device_table_ibfk_1` FOREIGN KEY (`device_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Áî®Êà∑ÁôªÂΩïËÆæÂ§áË°®';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_user_device_table`
--

LOCK TABLES `pcd_user_device_table` WRITE;
/*!40000 ALTER TABLE `pcd_user_device_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_user_device_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_user_info_table`
--

DROP TABLE IF EXISTS `pcd_user_info_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_user_info_table` (
  `user_name` varchar(120) NOT NULL COMMENT 'Áî®Êà∑Âêç',
  `user_id` binary(16) NOT NULL,
  `user_phone_number` varchar(50) NOT NULL,
  `user_image_path` varchar(512) DEFAULT NULL COMMENT 'Áî®Êà∑Â§¥ÂÉèË∑ØÂæÑ',
  `user_password` varchar(70) NOT NULL COMMENT 'Áî®Êà∑ÂØÜÁ†Å',
  `user_account` varchar(70) NOT NULL COMMENT 'Áî®Êà∑Ë¥¶Âè∑',
  `user_email` varchar(70) DEFAULT NULL COMMENT 'Áî®Êà∑ÈÇÆÁÆ±',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `user_phone_number` (`user_phone_number`),
  UNIQUE KEY `user_account` (`user_account`),
  UNIQUE KEY `user_email` (`user_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Áî®Êà∑‰ø°ÊÅØË°®';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_user_info_table`
--

LOCK TABLES `pcd_user_info_table` WRITE;
/*!40000 ALTER TABLE `pcd_user_info_table` DISABLE KEYS */;
INSERT INTO `pcd_user_info_table` VALUES ('ÊµãËØïÁî®Êà∑A',_binary '','18800000001',NULL,'$2a$12$GVVnuvG.aV5rhzYMEoey5.VbU9BX5BqsgaJMf8SypKZdu.olKYPFm','test_user_a','test_user_a@pcd.local'),('ÊµãËØïÁî®Êà∑B',_binary '\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"\"','18800000002',NULL,'$2a$12$GVVnuvG.aV5rhzYMEoey5.VbU9BX5BqsgaJMf8SypKZdu.olKYPFm','test_user_b','test_user_b@pcd.local'),('ÊµãËØïÁî®Êà∑C',_binary '3333333333333333','18800000003',NULL,'$2a$12$GVVnuvG.aV5rhzYMEoey5.VbU9BX5BqsgaJMf8SypKZdu.olKYPFm','test_user_c','test_user_c@pcd.local'),('XiaoMo',_binary 'A]0d§eHèB\÷Ò™õá¿','15777446691',NULL,'$2a$12$GVVnuvG.aV5rhzYMEoey5.VbU9BX5BqsgaJMf8SypKZdu.olKYPFm','pcd_18181999067','1773172144@qq.com'),('ÊµãËØïÁî®Êà∑D',_binary 'DDDDDDDDDDDDDDDD','18800000004',NULL,'$2a$12$GVVnuvG.aV5rhzYMEoey5.VbU9BX5BqsgaJMf8SypKZdu.olKYPFm','test_user_d','test_user_d@pcd.local'),('ÊµãËØïÁî®Êà∑E',_binary 'UUUUUUUUUUUUUUUU','18800000005',NULL,'$2a$12$GVVnuvG.aV5rhzYMEoey5.VbU9BX5BqsgaJMf8SypKZdu.olKYPFm','test_user_e','test_user_e@pcd.local');
/*!40000 ALTER TABLE `pcd_user_info_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_user_quota_log_table`
--

DROP TABLE IF EXISTS `pcd_user_quota_log_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_user_quota_log_table` (
  `quota_log_id` bigint NOT NULL AUTO_INCREMENT,
  `quota_log_user_id` binary(16) NOT NULL COMMENT 'Áî®Êà∑ID',
  `quota_log_change_type` varchar(20) NOT NULL COMMENT 'ÂèòÊõ¥Á±ªÂûã',
  `quota_log_change_bytes` bigint NOT NULL COMMENT 'ÂèòÊõ¥Â≠óËäÇÊï∞',
  `quota_log_before_total` bigint DEFAULT NULL COMMENT 'ÂèòÊõ¥ÂâçÊÄªÈ¢ùÂ∫¶',
  `quota_log_after_total` bigint DEFAULT NULL COMMENT 'ÂèòÊõ¥ÂêéÊÄªÈ¢ùÂ∫¶',
  `quota_log_before_used` bigint DEFAULT NULL COMMENT 'ÂèòÊõ¥ÂâçÂ∑≤Áî®',
  `quota_log_after_used` bigint DEFAULT NULL COMMENT 'ÂèòÊõ¥ÂêéÂ∑≤Áî®',
  `quota_log_operator` varchar(50) DEFAULT 'SYSTEM' COMMENT 'Êìç‰Ωú‰∫∫',
  `quota_log_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`quota_log_id`),
  KEY `idx_user_id_time` (`quota_log_user_id`,`quota_log_created_at`),
  CONSTRAINT `pcd_user_quota_log_table_ibfk_1` FOREIGN KEY (`quota_log_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ÈÖçÈ¢ùÂèòÊõ¥Êó•Âøó';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_user_quota_log_table`
--

LOCK TABLES `pcd_user_quota_log_table` WRITE;
/*!40000 ALTER TABLE `pcd_user_quota_log_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_user_quota_log_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_user_quota_table`
--

DROP TABLE IF EXISTS `pcd_user_quota_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_user_quota_table` (
  `quota_id` bigint NOT NULL AUTO_INCREMENT,
  `quota_user_id` binary(16) NOT NULL COMMENT 'Áî®Êà∑ID',
  `quota_total_capacity` bigint NOT NULL DEFAULT '10737418240' COMMENT 'ÊÄªÈ¢ùÂ∫¶',
  `quota_used_capacity` bigint NOT NULL DEFAULT '0' COMMENT 'Â∑≤Áî®ÂÆπÈáè',
  `quota_file_count` int NOT NULL DEFAULT '0' COMMENT 'Â∑≤‰∏ä‰º†Êñá‰ª∂Êï∞Èáè',
  `quota_version` int NOT NULL DEFAULT '0' COMMENT '‰πêËßÇÈîÅÁâàÊú¨Âè∑',
  `quota_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `quota_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`quota_id`),
  UNIQUE KEY `quota_user_id` (`quota_user_id`),
  KEY `idx_user_id` (`quota_user_id`),
  CONSTRAINT `pcd_user_quota_table_ibfk_1` FOREIGN KEY (`quota_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Áî®Êà∑Â≠òÂÇ®ÈÖçÈ¢ùË°®';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_user_quota_table`
--

LOCK TABLES `pcd_user_quota_table` WRITE;
/*!40000 ALTER TABLE `pcd_user_quota_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_user_quota_table` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-15  4:11:12
