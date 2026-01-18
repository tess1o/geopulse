/**
 * Composable for getting OIDC provider icons with hybrid approach
 * Supports custom icons from backend and smart detection based on provider names
 *
 * Icon types supported:
 * - CSS classes (e.g., 'pi pi-google') - PrimeIcons
 * - URLs (e.g., 'https://cdn.jsdelivr.net/...') - External images
 * - Local paths (e.g., '/icons/custom.png') - Local image files
 */
export function useProviderIcon() {

  /**
   * Default CSS icon class used as fallback
   */
  const DEFAULT_ICON = 'pi pi-sign-in';

  /**
   * Detects the type of icon based on its value
   * @param {string} iconValue - The icon value to analyze
   * @returns {'url'|'local'|'css'} The detected icon type
   */
  const getIconType = (iconValue) => {
    if (!iconValue || typeof iconValue !== 'string') {
      return 'css';
    }

    const trimmed = iconValue.trim();

    // Check for URL (http:// or https://)
    if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
      return 'url';
    }

    // Check for local path (starts with /)
    if (trimmed.startsWith('/')) {
      return 'local';
    }

    // Default to CSS class
    return 'css';
  };

  /**
   * Gets the raw icon value for a provider (without type detection)
   * @param {Object|string} provider - Provider object or name
   * @returns {string} The icon value (CSS class, URL, or local path)
   */
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
    return DEFAULT_ICON;
  };

  /**
   * Gets comprehensive icon information for a provider
   * @param {Object|string} provider - Provider object or name
   * @returns {{value: string, type: 'url'|'local'|'css'}} Icon value and type
   */
  const getProviderIconInfo = (provider) => {
    const value = getProviderIcon(provider);
    const type = getIconType(value);
    return { value, type };
  };

  return {
    getProviderIcon,
    getIconType,
    getProviderIconInfo,
    DEFAULT_ICON
  };
}
