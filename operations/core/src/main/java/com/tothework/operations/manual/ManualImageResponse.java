package com.tothework.operations.manual;

/**
 * 매뉴얼 이미지 한 장. 저장·삭제 요청에는 key를, 화면 표시에는 url을 쓴다.
 *
 * <p>둘 다 내려주는 이유: 클라이언트가 key만 받으면 방금 올린 이미지를 미리 볼 수 없고,
 * url만 받으면 수정 시 서버에 되돌려줄 key를 알 수 없다. 클라이언트가 버킷 주소를 알아서
 * 조립하게 하면 버킷·CDN을 바꿀 때 클라이언트까지 고쳐야 하므로 그 방법은 쓰지 않는다.
 */
public record ManualImageResponse(String key, String url) {
}
