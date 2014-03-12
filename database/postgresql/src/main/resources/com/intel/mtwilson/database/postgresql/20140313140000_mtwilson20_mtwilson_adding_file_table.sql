-- created 2014-03-05

-- This script creates the file table

CREATE  TABLE mw_file (
  id UUID NOT NULL ,
  name VARCHAR(255) NULL ,
  contentType VARCHAR(255) NULL ,
  content BLOB NULL ,
  PRIMARY KEY (id) );
  
INSERT INTO mw_changelog (ID, APPLIED_AT, DESCRIPTION) VALUES (20140313140000,NOW(),'Patch for creating the file table in the mtwilson database.');