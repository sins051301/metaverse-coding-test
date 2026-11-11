// Google 로그인은 프록시를 거치지 않고 백엔드로 직접 보낸다. Google 콘솔에 등록된 리다이렉트 주소가 백엔드(8080) 기준이기 때문이다.
export const BACKEND_ORIGIN = import.meta.env.VITE_BACKEND_ORIGIN
