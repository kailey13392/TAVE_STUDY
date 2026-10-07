-- prod(ddl-auto: validate) 용 테이블 생성 스크립트.
-- docker-compose 로 MySQL 을 처음 띄울 때 자동 실행된다.
CREATE TABLE IF NOT EXISTS item (
    item_id        BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(255) NOT NULL,
    price          INT          NOT NULL,
    stock_quantity INT          NOT NULL,
    PRIMARY KEY (item_id)
);
