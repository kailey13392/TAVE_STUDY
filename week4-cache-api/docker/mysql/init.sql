-- prod(ddl-auto: validate) 용 테이블 생성 스크립트.
-- docker-compose 로 MySQL 을 처음 띄울 때 자동 실행된다.
-- created_at / updated_at 은 BaseTimeEntity 가 채우는 컬럼.
CREATE TABLE IF NOT EXISTS item (
    item_id        BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(255) NOT NULL,
    price          INT          NOT NULL,
    stock_quantity INT          NOT NULL,
    created_at     DATETIME(6),
    updated_at     DATETIME(6),
    PRIMARY KEY (item_id)
);
