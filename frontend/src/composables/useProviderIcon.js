/**
 * Composable for getting OIDC provider icons with hybrid approach
 * Supports custom icons from backend and smart detection based on provider names
 */
export function useProviderIcon() {
  
  const getProviderIcon = (provider) => {
    // If provider object has icon field, use it (highest priority)
    if (typeof provider === 'object' && provider.icon) {
      return provider.icon;
    }
    
    // Extract provider name for smart detection
    const providerName = (typeof provider === 'object' ? provider.name : provider).toLowerCase();
    
    // Smart detection based on provider name patterns
    if (providerName.includes('google')) return 'pi pi-google';
    if (providerName.includes('microsoft') || providerName.includes('azure')) return 'pi pi-microsoft';
    if (providerName.includes('okta')) return 'pi pi-shield';
    if (providerName.includes('authentik')) return 'pi pi-lock';
    if (providerName.includes('keycloak')) return 'pi pi-key';
    if (providerName.includes('pocketid') || providerName.includes('pocket')) return 'pi pi-id-card';
    if (providerName.includes('auth0')) return 'pi pi-shield';
    if (providerName.includes('gitlab')) return 'pi pi-code';
    if (providerName.includes('github')) return 'pi pi-github';
    if (providerName.includes('discord')) return 'pi pi-discord';
    if (providerName.includes('slack')) return 'pi pi-slack';
    if (providerName.includes('facebook') || providerName.includes('meta')) return 'pi pi-facebook';
    if (providerName.includes('twitter') || providerName.includes('x.com')) return 'pi pi-twitter';
    if (providerName.includes('linkedin')) return 'pi pi-linkedin';
    if (providerName.includes('apple')) return 'pi pi-apple';
    if (providerName.includes('amazon') || providerName.includes('aws')) return 'pi pi-amazon';
    if (providerName.includes('salesforce')) return 'pi pi-cloud';
    if (providerName.includes('spotify')) return 'pi pi-volume-up';
    if (providerName.includes('twitch')) return 'pi pi-video';
    if (providerName.includes('steam')) return 'pi pi-desktop';
    if (providerName.includes('orcid')) return 'pi pi-user';
    if (providerName.includes('custom') || providerName.includes('generic')) return 'pi pi-sign-in';
    
    // Default fallback
    return 'pi pi-sign-in';
  };

  return {
    getProviderIcon
  };
}