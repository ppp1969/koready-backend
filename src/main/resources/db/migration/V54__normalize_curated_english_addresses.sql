UPDATE place_localizations english
JOIN places place
  ON place.id = english.place_id
JOIN place_localizations korean
  ON korean.place_id = place.id
 AND korean.language = 'KO'
SET english.address_text = CASE place.kto_content_id
    WHEN '126508' THEN '161 Sajik-ro, Jongno-gu, Seoul'
    WHEN '132183' THEN '88, Changgyeonggung-ro, Jongno-gu, Seoul'
    WHEN '129703' THEN '137 Seobinggo-ro, Yongsan-gu, Seoul'
    WHEN '125578' THEN '90 Minsokchon-ro, Giheung-gu, Yongin-si, Gyeonggi-do'
    WHEN '128758' THEN '514 Changhae-ro, Gangneung-si, Gangwon-do'
    WHEN '125949' THEN '280 Ungjin-ro, Gongju-si, Chungcheongnam-do'
    WHEN '506534' THEN '2282, Sinheuk-dong, Boryeong-si, Chungcheongnam-do'
    WHEN '264284' THEN '99 Girin-daero, Wansan-gu, Jeonju-si, Jeonbuk-do'
    WHEN '1997221' THEN '203 Gamnae 2-ro, Saha-gu, Busan'
    WHEN '126435' THEN '284-12 Ilchul-ro, Seogwipo-si, Jeju-do'
END
WHERE english.language = 'EN'
  AND english.translation_source = 'MANUAL_EDITED'
  AND place.kto_content_id IN (
      '126508',
      '132183',
      '129703',
      '125578',
      '128758',
      '125949',
      '506534',
      '264284',
      '1997221',
      '126435'
  )
  AND TRIM(english.address_text) = TRIM(korean.address_text);
