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
-- Table structure for table `pcd_directory_tree_table`
--

DROP TABLE IF EXISTS `pcd_directory_tree_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_directory_tree_table` (
  `node_id` varchar(36) NOT NULL,
  `node_user_id` varchar(36) NOT NULL,
  `node_parent_id` varchar(36) DEFAULT NULL,
  `node_name` varchar(200) NOT NULL,
  `node_create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `node_status` enum('lock','active','pending') DEFAULT 'active',
  PRIMARY KEY (`node_id`),
  KEY `node_user_id` (`node_user_id`),
  KEY `node_parent_id` (`node_parent_id`),
  CONSTRAINT `pcd_directory_tree_table_ibfk_1` FOREIGN KEY (`node_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_directory_tree_table_ibfk_2` FOREIGN KEY (`node_parent_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_directory_tree_table`
--

LOCK TABLES `pcd_directory_tree_table` WRITE;
/*!40000 ALTER TABLE `pcd_directory_tree_table` DISABLE KEYS */;
INSERT INTO `pcd_directory_tree_table` VALUES ('086462ca-48f8-4533-bbe9-016981784c38','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','上山','2025-12-03 14:35:29','lock'),('2063ea44-e2fd-491a-865d-4592e9f7324b','415d3064-a465-4813-8f42-d6f1aa9b87c0','d0c234c3-868b-4beb-9570-61ba51b141b6','1','2026-05-29 07:32:09','lock'),('2a3abed4-9407-410f-8ff5-8a95b754c6a5','415d3064-a465-4813-8f42-d6f1aa9b87c0','75cf49a4-f3a5-43d1-ae2b-9ce4577b09a3','1','2026-05-29 07:32:17','lock'),('2aa3b627-efe4-409a-a36a-00c8fd1a800b','415d3064-a465-4813-8f42-d6f1aa9b87c0','3ae9b5e5-96c0-40bd-95f7-23907363084b','66','2025-12-03 16:16:20','pending'),('2ecb5fa6-0133-4894-b6e7-b1c6928f25d2','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','是','2025-12-03 16:09:32','pending'),('316a48ee-d0c2-4167-8d5f-8824a4329dd1','415d3064-a465-4813-8f42-d6f1aa9b87c0','fe06b689-afa7-4bc2-865a-79a220226edf','不清楚','2025-12-03 16:17:18','pending'),('366e4da5-1a07-4c76-9de1-787d6a56d85f','415d3064-a465-4813-8f42-d6f1aa9b87c0','3ae9b5e5-96c0-40bd-95f7-23907363084b','55','2025-12-03 16:16:25','pending'),('3ae9b5e5-96c0-40bd-95f7-23907363084b','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','1','2025-12-03 07:08:18','lock'),('54b697ff-ed1c-4b48-a367-d909e3a887b7','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','7','2026-05-27 08:58:37','pending'),('568cb2a2-7451-47f9-8981-665d1a13894a','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','2','2025-12-03 07:12:49','pending'),('64ffc40d-125d-42ec-93d7-b9e52b0e690d','415d3064-a465-4813-8f42-d6f1aa9b87c0','2a3abed4-9407-410f-8ff5-8a95b754c6a5','1','2026-05-29 07:32:20','lock'),('6a21a480-7caa-4a0b-bb2c-70262a37db83','415d3064-a465-4813-8f42-d6f1aa9b87c0','2063ea44-e2fd-491a-865d-4592e9f7324b','1','2026-05-29 07:32:12','lock'),('6b827495-f87a-45ea-9e01-013efb9fc40a','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','小吃','2025-12-03 14:39:42','pending'),('73783657-3893-47a5-ba69-d3a1d285a30a','415d3064-a465-4813-8f42-d6f1aa9b87c0','a0c0fe38-32f5-4424-9a78-c0ff23f2fc14','1','2026-05-29 07:32:38','pending'),('75cf49a4-f3a5-43d1-ae2b-9ce4577b09a3','415d3064-a465-4813-8f42-d6f1aa9b87c0','6a21a480-7caa-4a0b-bb2c-70262a37db83','1','2026-05-29 07:32:14','lock'),('795b8488-4d3e-48c4-aa2a-b3108911d08a','415d3064-a465-4813-8f42-d6f1aa9b87c0','a0a01fc1-529d-40da-9160-39aefeae5395','1','2026-05-29 07:32:32','lock'),('81a957cb-7580-4ad0-83fd-c6d651b20fb3','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','333','2025-12-03 14:32:31','pending'),('a0a01fc1-529d-40da-9160-39aefeae5395','415d3064-a465-4813-8f42-d6f1aa9b87c0','a436ceab-6339-417b-9a19-41c8f5d8ea97','1','2026-05-29 07:32:29','lock'),('a0c0fe38-32f5-4424-9a78-c0ff23f2fc14','415d3064-a465-4813-8f42-d6f1aa9b87c0','795b8488-4d3e-48c4-aa2a-b3108911d08a','1','2026-05-29 07:32:35','lock'),('a436ceab-6339-417b-9a19-41c8f5d8ea97','415d3064-a465-4813-8f42-d6f1aa9b87c0','e7299a97-8ed7-4a83-8d2f-ac7e93d26f3d','1','2026-05-29 07:32:25','lock'),('b69ffe75-d8cd-431b-9195-eee41ac7b9c5','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','244','2025-12-03 07:12:53','pending'),('bf07088f-ae01-422a-a9a3-997474e7f483','415d3064-a465-4813-8f42-d6f1aa9b87c0','fe06b689-afa7-4bc2-865a-79a220226edf','22','2025-12-03 16:16:52','pending'),('d0c234c3-868b-4beb-9570-61ba51b141b6','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','sss','2025-12-03 14:32:38','lock'),('d8785081-e2bf-4ced-97fa-3b785de3abc6','415d3064-a465-4813-8f42-d6f1aa9b87c0',NULL,'#root','2025-08-02 15:40:15','lock'),('e7299a97-8ed7-4a83-8d2f-ac7e93d26f3d','415d3064-a465-4813-8f42-d6f1aa9b87c0','64ffc40d-125d-42ec-93d7-b9e52b0e690d','1','2026-05-29 07:32:23','lock'),('eb1c76bb-9687-4a03-a182-98d22963d867','415d3064-a465-4813-8f42-d6f1aa9b87c0','086462ca-48f8-4533-bbe9-016981784c38','11','2025-12-03 16:16:57','pending'),('f27f84ca-3d35-4d93-80ff-9b3582214dc9','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','最爱言言','2025-12-06 13:58:39','pending'),('fe06b689-afa7-4bc2-865a-79a220226edf','415d3064-a465-4813-8f42-d6f1aa9b87c0','d8785081-e2bf-4ced-97fa-3b785de3abc6','2445','2025-12-03 08:12:20','lock');
/*!40000 ALTER TABLE `pcd_directory_tree_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_file_info_table`
--

DROP TABLE IF EXISTS `pcd_file_info_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_file_info_table` (
  `file_name` varchar(150) NOT NULL,
  `file_uploaded_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `file_size` bigint NOT NULL,
  `file_type` varchar(60) NOT NULL,
  `file_author_id` varchar(36) NOT NULL,
  `file_id` varchar(36) NOT NULL,
  `file_checksum` varchar(256) NOT NULL,
  `file_node_id` varchar(36) NOT NULL,
  `file_total_chunks` int NOT NULL,
  `file_storage_path` varchar(512) NOT NULL,
  PRIMARY KEY (`file_id`),
  KEY `fk_file_info_user_info` (`file_author_id`),
  KEY `fk_file_info_directory_tree` (`file_node_id`),
  CONSTRAINT `fk_file_info_directory_tree` FOREIGN KEY (`file_node_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_file_info_user_info` FOREIGN KEY (`file_author_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_file_info_table`
--

LOCK TABLES `pcd_file_info_table` WRITE;
/*!40000 ALTER TABLE `pcd_file_info_table` DISABLE KEYS */;
INSERT INTO `pcd_file_info_table` VALUES ('IMG_6353.CR3','2026-05-28 07:33:12',27715298,'image/x-canon-cr3','415d3064-a465-4813-8f42-d6f1aa9b87c0','05a6f874-767b-4ed6-8e2b-a31ac803928c','bc43a9036be1c6c1f28393e53b66b91ebad6b2217c06eb64bdf164e690c0d48d','d8785081-e2bf-4ced-97fa-3b785de3abc6',6,'../Uploads/storage/8697cc8f-e499-4f23-a54c-2c287fc4b98c-6.cloud'),('IMG_0949.jpeg','2025-08-05 06:40:57',4827466,'image/jpeg','415d3064-a465-4813-8f42-d6f1aa9b87c0','084903ee-0755-46f0-933f-57f770019a5f','e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855','d8785081-e2bf-4ced-97fa-3b785de3abc6',11,'../Uploads/storage/ae263d21-8da1-44c9-a4bf-8408a71a908f-1.jpeg'),('Lightroom Catalog-v13-4-2 Sync.lrdata.zip','2026-05-28 07:24:27',165624,'application/zip','415d3064-a465-4813-8f42-d6f1aa9b87c0','0c65b4b6-8614-4745-93c4-d8dc4899ceae','210839d9a95c32083f5ac1b96755e1627afddf71206eefdc431e7c8ed33f2d65','d8785081-e2bf-4ced-97fa-3b785de3abc6',1,'../Uploads/storage/41813883-6d3d-466f-bb9b-21c474b172c4-1.cloud'),('java_error_in_idea.hprof','2026-05-28 15:13:21',837500268,'application/octet-stream','415d3064-a465-4813-8f42-d6f1aa9b87c0','2072cea6-5969-4058-a8aa-a3229716b1b3','656e432b59fbaf13f6f3965cde68d155cf7c6afcdb7a1f3373440de34a436c37','d8785081-e2bf-4ced-97fa-3b785de3abc6',160,'../Uploads/storage/b41c0af9-a8fa-4123-8749-2634aeb9eb1b-160.cloud'),('Lightroom Catalog-v13-4 Previews.lrdata.zip','2026-05-28 07:20:19',3895,'application/zip','415d3064-a465-4813-8f42-d6f1aa9b87c0','34b2f654-2685-4173-802a-ea84dff24392','9341053ec796410231010b5741bffa2aa2a78f8bed5d1540722e7bb18933ad02','d8785081-e2bf-4ced-97fa-3b785de3abc6',1,'../Uploads/storage/f81e6ff9-2ff1-4dc6-a87b-6da4020fb67e-1.cloud'),('jbr_err_pid49370.log','2026-05-28 05:03:06',2843,'application/octet-stream','415d3064-a465-4813-8f42-d6f1aa9b87c0','34e96180-38bc-43a1-8663-789ef78151a8','20dff912b16e7f771f77d00055707a3f0fbe9d3b34450a73e9b6adea37dec7df','d8785081-e2bf-4ced-97fa-3b785de3abc6',1,'../Uploads/storage/30bf758d-df49-4231-8206-db0720cf7147-1.cloud'),('jbr_err_pid49370.log','2026-05-27 20:57:05',2843,'application/octet-stream','415d3064-a465-4813-8f42-d6f1aa9b87c0','3b7cf432-8a9a-480c-9bf8-e4b9d2a4e7a9','20dff912b16e7f771f77d00055707a3f0fbe9d3b34450a73e9b6adea37dec7df','3ae9b5e5-96c0-40bd-95f7-23907363084b',1,'../Uploads/storage/b4e99892-cbf6-4366-a880-866a87950e4a-1.cloud'),('2025-6-5 00.52拍摄的照片 #2.jpg','2026-06-01 13:28:17',331238,'image/jpeg','415d3064-a465-4813-8f42-d6f1aa9b87c0','4177dd59-cd60-4887-a4e7-bc30c84e530b','c422b7b91bd0dc110a885183437aa5e2a18d290359549a1473dce066ad107c89','086462ca-48f8-4533-bbe9-016981784c38',1,'../Uploads/storage/26a8554d-8415-4c5d-b757-fd6880de9cf9-1.cloud'),('Lightroom Catalog-v13-4-2.lrcat-data.zip','2026-05-28 07:36:38',174315880,'application/zip','415d3064-a465-4813-8f42-d6f1aa9b87c0','57669c93-30c2-45b3-ab05-4b78d94bfd93','e625ee4918fdc0e3907d8038acc187e7d200c795427bb4c325c3e932c6c1da9f','d8785081-e2bf-4ced-97fa-3b785de3abc6',34,'../Uploads/storage/bd6a8097-c81b-482d-8d34-2dded07e19ff-34.cloud'),('README.md','2026-05-27 20:52:33',4909,'text/markdown','415d3064-a465-4813-8f42-d6f1aa9b87c0','587f087f-5a1d-487d-b909-4d6dda0d410c','83b32e8e1f77a60dae9d1014b545cd1d58d9db1e05e4c3189ffd67fa6a857d7c','d8785081-e2bf-4ced-97fa-3b785de3abc6',1,'../Uploads/storage/6228b3f2-4093-419c-bc97-5541d127433a-1.cloud'),('DMG_6351.CR3','2026-05-28 07:30:01',27214121,'image/x-canon-cr3','415d3064-a465-4813-8f42-d6f1aa9b87c0','670becf3-7115-4f44-a46b-75057ef4de81','9a68dcaade53304c8160ea3bba8ae8feae54bc960a78f26d7d90881b0f0feaab','d8785081-e2bf-4ced-97fa-3b785de3abc6',6,'../Uploads/storage/9a8c206a-0623-4dc6-b51d-1883caca8f41-6.cloud'),('Lightroom Catalog-v13-4 Helper.lrdata.zip','2026-05-28 07:21:43',10014,'application/zip','415d3064-a465-4813-8f42-d6f1aa9b87c0','6a3ac610-dff7-41db-b979-473bd35ad91e','c744f7f5f10042c62eb0b74cfef3291bf88bea39cb9f31318b16f4280fe2afde','d8785081-e2bf-4ced-97fa-3b785de3abc6',1,'../Uploads/storage/b37f57ef-f30e-43f9-9e6c-6f13ab5e1080-1.cloud'),('IMG_6343.CR3','2026-05-28 07:29:49',26436677,'image/x-canon-cr3','415d3064-a465-4813-8f42-d6f1aa9b87c0','82d55595-1d81-47f7-99bd-604e147e370f','92635eac756caefd95e660017bf921f013d26be70d0b7c28e628c85973f22a0f','d8785081-e2bf-4ced-97fa-3b785de3abc6',6,'../Uploads/storage/808fccb6-0288-46da-bbd0-851791fbdcd4-6.cloud'),('Lightroom Catalog-v13-4-2.lrcat','2026-05-28 07:24:38',13836288,'application/octet-stream','415d3064-a465-4813-8f42-d6f1aa9b87c0','850c6a50-1234-4f05-b430-a3a6842c5e2d','895141bec63fc9360f54c32011acf2fa4ca9f6631209f11337d008cc4a4ad547','d8785081-e2bf-4ced-97fa-3b785de3abc6',3,'../Uploads/storage/f1e169fe-a4e9-4095-8664-28794626d0f0-3.cloud'),('IMG_6434.CR3','2026-05-28 07:17:21',28566841,'image/x-canon-cr3','415d3064-a465-4813-8f42-d6f1aa9b87c0','a1c31506-b868-468b-aeae-cad86e3dab78','5bcb6fbb0e8ab74a8f9a95864da16d3111ab88e575f7302b7dad73c85627b80a','d8785081-e2bf-4ced-97fa-3b785de3abc6',6,'../Uploads/storage/4b41998f-a25c-4e6e-aac9-c19ca226ed11-6.cloud'),('Lightroom Catalog-v13-4.lrcat','2026-05-28 07:24:16',1740800,'application/octet-stream','415d3064-a465-4813-8f42-d6f1aa9b87c0','a84490ee-cedf-4f45-8663-e2043bb099e1','a8be0dbea7de57112fb71351fc35aec6c290878a0b57fa8890a31256e25f0bc1','d8785081-e2bf-4ced-97fa-3b785de3abc6',1,'../Uploads/storage/7d1df73a-133f-4493-9a86-940c4002eb1c-1.cloud'),('SquareLine_Studio.app.zip','2026-05-29 07:41:33',146990152,'application/zip','415d3064-a465-4813-8f42-d6f1aa9b87c0','d284b6e6-bc70-46bf-accb-86263b55ce52','acb48ab8e667c13dd3ec19eb2da9493da64673332b7758cca0abe624f5dc1cf8','d8785081-e2bf-4ced-97fa-3b785de3abc6',29,'../Uploads/storage/e1c821f0-91cf-4d3c-8234-5d0b17822dca-29.cloud'),('IMG_6435.CR3','2026-05-28 07:16:39',26037019,'image/x-canon-cr3','415d3064-a465-4813-8f42-d6f1aa9b87c0','d511b15d-4495-4571-91d9-c040067503f5','af4fad4819184d704fe637e7a4f8d9b66f6710e14b3a99c0a2240e933b6ed48d','d8785081-e2bf-4ced-97fa-3b785de3abc6',5,'../Uploads/storage/779d1228-5297-4d77-a310-1f61ceed9d04-5.cloud'),('Mobile Downloads.lrdata.zip','2026-05-28 07:23:39',9066585,'application/zip','415d3064-a465-4813-8f42-d6f1aa9b87c0','d976c4ae-5019-43bf-99b4-be79eee8be8a','1d4b60c9f0b8e91438c44d3166d2ba5dadcc6b068e2f5a2b3a0389a72cdd9f46','d8785081-e2bf-4ced-97fa-3b785de3abc6',2,'../Uploads/storage/70b41d92-c912-475d-a829-516eebda1ec8-2.cloud'),('2025-6-22 01.23拍摄的照片.jpg','2026-05-28 07:19:51',334915,'image/jpeg','415d3064-a465-4813-8f42-d6f1aa9b87c0','df345ddd-3078-428c-8fb1-3a0611f26d98','eccaed1d9f2c6d90a65edc8d79a57d52a7183cadf0fedcded1b753df14c6d24e','d8785081-e2bf-4ced-97fa-3b785de3abc6',1,'../Uploads/storage/34118e15-eed4-4e37-acaa-9e7d1e8d624e-1.cloud'),('Lightroom Catalog-v13-4-2 Previews.lrdata.zip','2026-05-28 07:31:36',1190963501,'application/zip','415d3064-a465-4813-8f42-d6f1aa9b87c0','fd46a286-b817-42dd-9a73-4fae50ae35f7','880b4e3329102b3c7c83ccba172e85e52dd2675752e5f0c41917f9b4c0684028','d8785081-e2bf-4ced-97fa-3b785de3abc6',228,'../Uploads/storage/6277a4bf-94b0-472e-9d93-44004508a53c-228.cloud');
/*!40000 ALTER TABLE `pcd_file_info_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_sharing_Link_mange_table`
--

DROP TABLE IF EXISTS `pcd_sharing_Link_mange_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_sharing_Link_mange_table` (
  `sharing_link_id` varchar(36) NOT NULL,
  `sharing_link_path` varchar(512) NOT NULL,
  `sharing_link_file_id` varchar(36) NOT NULL,
  `sharing_link_valid_starting_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `sharing_link_valid_endding_time` timestamp NULL DEFAULT NULL,
  `sharing_link_password` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`sharing_link_id`),
  KEY `fk_sharing_link_file_info` (`sharing_link_file_id`),
  CONSTRAINT `fk_sharing_link_file_info` FOREIGN KEY (`sharing_link_file_id`) REFERENCES `pcd_file_info_table` (`file_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_sharing_Link_mange_table`
--

LOCK TABLES `pcd_sharing_Link_mange_table` WRITE;
/*!40000 ALTER TABLE `pcd_sharing_Link_mange_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_sharing_Link_mange_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_upload_chunks_table`
--

DROP TABLE IF EXISTS `pcd_upload_chunks_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_upload_chunks_table` (
  `chunk_uploads_id` varchar(36) NOT NULL,
  `chunk_index` int NOT NULL,
  `chunk_status` enum('pending','uploading','uploaded','failed') DEFAULT 'pending',
  `chunk_storage_path` varchar(512) NOT NULL,
  `chunk_uploaded_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`chunk_uploads_id`,`chunk_index`),
  CONSTRAINT `fk_chunks_uploads_session` FOREIGN KEY (`chunk_uploads_id`) REFERENCES `pcd_uploads_session_table` (`uploads_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_upload_chunks_table`
--

LOCK TABLES `pcd_upload_chunks_table` WRITE;
/*!40000 ALTER TABLE `pcd_upload_chunks_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `pcd_upload_chunks_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_uploads_session_table`
--

DROP TABLE IF EXISTS `pcd_uploads_session_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_uploads_session_table` (
  `uploads_id` varchar(36) NOT NULL,
  `uploads_user_id` varchar(36) NOT NULL,
  `uploads_total_chunks` int NOT NULL,
  `uploads_starting_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `uploads_endding_time` timestamp NOT NULL,
  `uploads_file_size` bigint NOT NULL,
  `uploads_file_checksum` varchar(256) NOT NULL,
  `uploads_chunks_max_size` int NOT NULL,
  `uploads_file_name` varchar(150) NOT NULL,
  `uploads_file_type` varchar(60) NOT NULL,
  `uploads_node_id` varchar(36) NOT NULL,
  `uploads_status` enum('uploading','merging','completed','failed') DEFAULT 'uploading',
  PRIMARY KEY (`uploads_id`),
  KEY `fk_uploads_session_user_info` (`uploads_user_id`),
  KEY `fk_uploads_session_directory_tree` (`uploads_node_id`),
  CONSTRAINT `fk_uploads_session_directory_tree` FOREIGN KEY (`uploads_node_id`) REFERENCES `pcd_directory_tree_table` (`node_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_uploads_session_user_info` FOREIGN KEY (`uploads_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_uploads_session_table`
--

LOCK TABLES `pcd_uploads_session_table` WRITE;
/*!40000 ALTER TABLE `pcd_uploads_session_table` DISABLE KEYS */;
INSERT INTO `pcd_uploads_session_table` VALUES ('06d7e093-8030-42ce-a2da-4e69256b72e3','415d3064-a465-4813-8f42-d6f1aa9b87c0',1,'2026-06-01 13:22:41','2026-06-01 13:52:41',331238,'c422b7b91bd0dc110a885183437aa5e2a18d290359549a1473dce066ad107c89',5242880,'2025-6-5 00.52拍摄的照片 #2.jpg','image/jpeg','086462ca-48f8-4533-bbe9-016981784c38','uploading'),('64ea97a2-40c6-4be3-aa4a-48cc550ed796','415d3064-a465-4813-8f42-d6f1aa9b87c0',1,'2026-06-01 13:19:04','2026-06-01 13:49:04',334915,'eccaed1d9f2c6d90a65edc8d79a57d52a7183cadf0fedcded1b753df14c6d24e',5242880,'2025-6-22 01.23拍摄的照片.jpg','image/jpeg','086462ca-48f8-4533-bbe9-016981784c38','uploading'),('a4bf8b59-31d3-4078-a00d-f75c689a25a1','415d3064-a465-4813-8f42-d6f1aa9b87c0',1,'2026-06-01 13:17:00','2026-06-01 13:47:00',2347523,'76dd0b2b3f3bb97413641c7a3b67fbf3792c35a1e1537d3d2b4890848bece6da',5242880,'background-2.jpg','image/jpeg','d8785081-e2bf-4ced-97fa-3b785de3abc6','uploading'),('a960c889-3b33-4d0a-a953-f1e1c36d6812','415d3064-a465-4813-8f42-d6f1aa9b87c0',1,'2026-06-01 13:24:43','2026-06-01 13:54:43',331238,'c422b7b91bd0dc110a885183437aa5e2a18d290359549a1473dce066ad107c89',5242880,'2025-6-5 00.52拍摄的照片 #2.jpg','image/jpeg','2ecb5fa6-0133-4894-b6e7-b1c6928f25d2','uploading');
/*!40000 ALTER TABLE `pcd_uploads_session_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_user_info_table`
--

DROP TABLE IF EXISTS `pcd_user_info_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_user_info_table` (
  `user_name` varchar(120) NOT NULL,
  `user_id` varchar(36) NOT NULL,
  `user_phone_number` varchar(50) NOT NULL,
  `user_image_path` varchar(512) DEFAULT NULL,
  `user_password` varchar(70) NOT NULL,
  `user_account` varchar(70) NOT NULL,
  `user_email` varchar(70) DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_user_phone_number` (`user_phone_number`),
  UNIQUE KEY `uk_user_account` (`user_account`),
  UNIQUE KEY `uk_user_email` (`user_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pcd_user_info_table`
--

LOCK TABLES `pcd_user_info_table` WRITE;
/*!40000 ALTER TABLE `pcd_user_info_table` DISABLE KEYS */;
INSERT INTO `pcd_user_info_table` VALUES ('XiaoMo','415d3064-a465-4813-8f42-d6f1aa9b87c0','15777446691',NULL,'$2y$10$EF.UYBZylYuTWCfUkPpy4O6s4fWLUJwXDIwmLwXhO6.k/f.pipIzG','pcd_18181999067','1773172144@qq.com');
/*!40000 ALTER TABLE `pcd_user_info_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pcd_user_device_table`
--

DROP TABLE IF EXISTS `pcd_user_device_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_user_device_table` (
  `device_id` varchar(36) NOT NULL COMMENT '服务端生成的设备ID',
  `device_user_id` varchar(36) NOT NULL COMMENT '所属用户ID',
  `device_client_type` varchar(50) NOT NULL COMMENT '客户端类型，例如 WEB/IOS/MACOS/WECHAT/PC',
  `device_client_name` varchar(120) DEFAULT NULL COMMENT '客户端展示名称',
  `device_platform` varchar(120) DEFAULT NULL COMMENT '系统或平台信息',
  `device_user_agent_hash` varchar(64) DEFAULT NULL COMMENT 'User-Agent规范化后的哈希',
  `device_public_key` text COMMENT '设备密钥绑定的公钥，可选',
  `device_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `device_last_seen_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `device_status` enum('active','disabled','revoked') NOT NULL DEFAULT 'active',
  PRIMARY KEY (`device_id`),
  KEY `idx_device_user_status` (`device_user_id`,`device_status`),
  CONSTRAINT `pcd_user_device_table_ibfk_1` FOREIGN KEY (`device_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户登录设备表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pcd_login_session_table`
--

DROP TABLE IF EXISTS `pcd_login_session_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_login_session_table` (
  `login_session_id` varchar(36) NOT NULL COMMENT '服务端签发的登录会话ID，即sid',
  `login_session_user_id` varchar(36) NOT NULL COMMENT '登录用户ID',
  `login_session_device_id` varchar(36) DEFAULT NULL COMMENT '关联设备ID',
  `login_session_token_jti` varchar(36) DEFAULT NULL COMMENT '登录JWT jti',
  `login_session_client_ip` varchar(64) DEFAULT NULL COMMENT '登录IP',
  `login_session_user_agent` varchar(512) DEFAULT NULL COMMENT '登录User-Agent',
  `login_session_started_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `login_session_expires_at` datetime NOT NULL COMMENT '会话过期时间',
  `login_session_revoked_at` datetime DEFAULT NULL COMMENT '会话撤销时间',
  `login_session_status` enum('active','expired','revoked') NOT NULL DEFAULT 'active',
  PRIMARY KEY (`login_session_id`),
  KEY `idx_login_session_user_status` (`login_session_user_id`,`login_session_status`),
  KEY `idx_login_session_device_status` (`login_session_device_id`,`login_session_status`),
  KEY `idx_login_session_jti` (`login_session_token_jti`),
  CONSTRAINT `pcd_login_session_table_ibfk_1` FOREIGN KEY (`login_session_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `pcd_login_session_table_ibfk_2` FOREIGN KEY (`login_session_device_id`) REFERENCES `pcd_user_device_table` (`device_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户登录会话表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pcd_login_audit_table`
--

DROP TABLE IF EXISTS `pcd_login_audit_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_login_audit_table` (
  `audit_id` bigint NOT NULL AUTO_INCREMENT,
  `audit_user_id` varchar(36) DEFAULT NULL COMMENT '匹配到的用户ID，失败时可为空',
  `audit_account` varchar(100) DEFAULT NULL COMMENT '登录账号',
  `audit_phone_number` varchar(50) DEFAULT NULL COMMENT '登录手机号',
  `audit_success` tinyint(1) NOT NULL COMMENT '是否登录成功',
  `audit_failure_reason` varchar(120) DEFAULT NULL COMMENT '失败原因',
  `audit_client_ip` varchar(64) DEFAULT NULL COMMENT '客户端IP',
  `audit_user_agent` varchar(512) DEFAULT NULL COMMENT 'User-Agent',
  `audit_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`audit_id`),
  KEY `idx_login_audit_user_time` (`audit_user_id`,`audit_created_at`),
  KEY `idx_login_audit_account_time` (`audit_account`,`audit_created_at`),
  KEY `idx_login_audit_ip_time` (`audit_client_ip`,`audit_created_at`),
  CONSTRAINT `pcd_login_audit_table_ibfk_1` FOREIGN KEY (`audit_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录审计表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pcd_user_quota_log_table`
--

DROP TABLE IF EXISTS `pcd_user_quota_log_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pcd_user_quota_log_table` (
  `quota_log_id` bigint NOT NULL AUTO_INCREMENT,
  `quota_log_user_id` varchar(36) NOT NULL COMMENT '用户ID，关联用户表',
  `quota_log_change_type` varchar(20) NOT NULL COMMENT '变更类型：EXPAND-扩容，REDUCE-缩容，FILE_UPLOAD-文件上传，FILE_DELETE-文件删除',
  `quota_log_change_bytes` bigint NOT NULL COMMENT '变更字节数（正为增加，负为减少）',
  `quota_log_before_total` bigint DEFAULT NULL COMMENT '变更前总额度',
  `quota_log_after_total` bigint DEFAULT NULL COMMENT '变更后总额度',
  `quota_log_before_used` bigint DEFAULT NULL COMMENT '变更前已用',
  `quota_log_after_used` bigint DEFAULT NULL COMMENT '变更后已用',
  `quota_log_operator` varchar(50) DEFAULT 'SYSTEM' COMMENT '操作人（管理员或系统）',
  `quota_log_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`quota_log_id`),
  KEY `idx_user_id_time` (`quota_log_user_id`,`quota_log_created_at`),
  CONSTRAINT `pcd_user_quota_log_table_ibfk_1` FOREIGN KEY (`quota_log_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配额变更日志';
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
  `quota_user_id` varchar(36) NOT NULL COMMENT '用户ID，关联用户表',
  `quota_total_capacity` bigint NOT NULL DEFAULT '10737418240' COMMENT '总额度（字节），默认10GB = 10*1024^3',
  `quota_used_capacity` bigint NOT NULL DEFAULT '0' COMMENT '已用容量（字节）',
  `quota_file_count` int NOT NULL DEFAULT '0' COMMENT '已上传文件数量',
  `quota_version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `quota_created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `quota_updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`quota_id`),
  UNIQUE KEY `quota_user_id` (`quota_user_id`),
  KEY `idx_user_id` (`quota_user_id`),
  CONSTRAINT `pcd_user_quota_table_ibfk_1` FOREIGN KEY (`quota_user_id`) REFERENCES `pcd_user_info_table` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户存储配额表';
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

-- Dump completed on 2026-06-05 20:37:05
