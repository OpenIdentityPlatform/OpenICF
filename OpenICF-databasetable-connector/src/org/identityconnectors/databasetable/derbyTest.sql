create table Accounts (
  accountId   VARCHAR(50) NOT NULL,
  password    VARCHAR(50),
  manager     VARCHAR(50),
  middlename  VARCHAR(50),
  firstname   VARCHAR(50),
  lastname    VARCHAR(50),
  email       VARCHAR(250),
  department  VARCHAR(250),
  title       VARCHAR(250),
  age         INTEGER,
  accessed    BIGINT,
  salary      DECIMAL(7,2),
  jpegphoto   BLOB,
  enrolled    TIMESTAMP,      
  changed     TIMESTAMP      
)
