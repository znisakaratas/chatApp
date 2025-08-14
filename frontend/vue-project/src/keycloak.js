
import Keycloak from 'keycloak-js';

const keycloak = new Keycloak({
    url: 'http://localhost:8081/',
    realm: 'springboot-test',
    clientId: 'test-rest-api'
});
export const keycloakInitOptions = {
    onLoad: 'login-required',
    checkLoginIframe: false,
    pkceMethod: 'S256'
  }
  
export default keycloak;
