-- 4번 지문의 HTML 태그(10<sup>-6</sup>)를 유니코드 위첨자로 바꾼다.
-- 지문 렌더러(react-markdown)는 HTML 을 해석하지 않아 태그가 글자 그대로 보였다.
-- V2 를 고치면 이미 적용한 DB 에서 Flyway 체크섬이 달라 시작이 막히므로 새 마이그레이션으로 고친다.

UPDATE problems
SET description = REPLACE(description, '10<sup>-6</sup>', '10⁻⁶')
WHERE id = 4;
