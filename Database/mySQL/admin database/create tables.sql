DROP TABLE IF EXISTS enterprise_admin.ActiveItem;
DROP TABLE IF EXISTS enterprise_admin.Audit;
DROP TABLE IF EXISTS enterprise_admin.DependencyType;
DROP TABLE IF EXISTS enterprise_admin.Item;
DROP TABLE IF EXISTS enterprise_admin.ItemDependency;
DROP TABLE IF EXISTS enterprise_admin.ItemType;
DROP TABLE IF EXISTS enterprise_data_pseudonymised.ReportPatients;
DROP TABLE IF EXISTS enterprise_data_pseudonymised.ReportResult;

CREATE TABLE enterprise_admin.ActiveItem (
	ActiveItemUuid char(36) NOT NULL,
	OrganisationUuid char(36) NOT NULL,
	ItemUuid char(36) NOT NULL,
	AuditUuid char(36) NOT NULL,
	ItemTypeId smallint NOT NULL,
	IsDeleted TINYINT(1) NOT NULL,
	PRIMARY KEY (ActiveItemUuid)
);
CREATE TABLE enterprise_admin.Audit (
	AuditUuid char(36) NOT NULL,
	EndUserUuid char(36) NOT NULL,
	TimeStamp timestamp NOT NULL,
	OrganisationUuid char(36) NOT NULL,
	PRIMARY KEY (AuditUuid)
);
CREATE TABLE enterprise_admin.DependencyType (
	DependencyTypeId smallint NOT NULL,
	Description varchar(100) NOT NULL,
	PRIMARY KEY (DependencyTypeId)
);
CREATE TABLE enterprise_admin.Item (
	ItemUuid char(36) NOT NULL,
	AuditUuid char(36) NOT NULL,
	XmlContent text NOT NULL,
	Title varchar(255) NOT NULL,
	Description text NOT NULL,
	IsDeleted TINYINT(1) NOT NULL,
	PRIMARY KEY (AuditUuid,ItemUuid)
);
CREATE TABLE enterprise_admin.ItemDependency (
	ItemUuid char(36) NOT NULL,
	AuditUuid char(36) NOT NULL,
	DependentItemUuid char(36) NOT NULL,
	DependencyTypeId smallint NOT NULL,
	PRIMARY KEY (AuditUuid,DependentItemUuid,ItemUuid)
);
CREATE TABLE enterprise_admin.ItemType (
	ItemTypeId smallint NOT NULL,
	Description varchar(100) NOT NULL,
	PRIMARY KEY (ItemTypeId)
);
CREATE TABLE enterprise_data_pseudonymised.`ReportPatients` (
  `ReportPatientId` int(11) NOT NULL AUTO_INCREMENT,
  `RunDate` timestamp NULL DEFAULT NULL,
  `QueryItemUuid` char(36) NOT NULL,
  `OrganisationId` smallint(6) NOT NULL,
  `PatientId` BIGINT(20) NOT NULL,
  `PseudoId` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`ReportPatientId`),
  KEY `RunDate` (`RunDate`),
  KEY `QueryItemUuid` (`QueryItemUuid`),
  KEY `OrganisationId` (`OrganisationId`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;
CREATE TABLE enterprise_data_pseudonymised.`ReportResult` (
  `ReportResultId` int(11) NOT NULL AUTO_INCREMENT,
  `EndUserUuid` char(36) NOT NULL,
  `BaselineDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `RunDate` timestamp NULL DEFAULT NULL,
  `OrganisationId` smallint(6) NOT NULL,
  `QueryItemUuid` char(36) NOT NULL,
  `PopulationTypeId` tinyint(1) NOT NULL,
  `DenominatorCount` int(11) DEFAULT NULL,
  `EnumeratorCount` int(11) DEFAULT NULL,
  PRIMARY KEY (`ReportResultId`),
  KEY `RunDate` (`RunDate`),
  KEY `QueryItemUuid` (`QueryItemUuid`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=latin1;

INSERT INTO enterprise_admin.ItemType(ItemTypeId, Description) VALUES (5, 'CodeSet');
INSERT INTO enterprise_admin.ItemType(ItemTypeId, Description) VALUES (4, 'DataSource');
INSERT INTO enterprise_admin.ItemType(ItemTypeId, Description) VALUES (7, 'LibraryFolder');
INSERT INTO enterprise_admin.ItemType(ItemTypeId, Description) VALUES (2, 'Query');
INSERT INTO enterprise_admin.ItemType(ItemTypeId, Description) VALUES (3, 'Test');

INSERT INTO enterprise_admin.DependencyType(DependencyTypeId, Description) VALUES (0, 'IsChildOf');
INSERT INTO enterprise_admin.DependencyType(DependencyTypeId, Description) VALUES (1, 'IsContainedWithin');
INSERT INTO enterprise_admin.DependencyType(DependencyTypeId, Description) VALUES (2, 'Uses');


