import { t } from '@/locales'

const getFavoriteName = (favorite, pending = false) => (
  favorite?.name || (pending ? t('maps.popups.favorite.pendingName') : t('maps.popups.favorite.name'))
)

const getFavoriteKindLabel = ({ pending = false, isArea = false } = {}) => {
  if (pending && isArea) return t('maps.popups.favorite.pendingArea')
  if (pending) return t('maps.popups.favorite.pendingPoint')
  if (isArea) return t('maps.popups.favorite.areaFavorite')
  return t('maps.popups.favorite.favoritePoint')
}

export const buildFavoriteManagementPopupModel = (
  favorite,
  {
    pending = false,
    isArea = false
  } = {}
) => {
  const rows = [
    favorite?.category
      ? {
          label: t('maps.popups.favorite.category'),
          value: favorite.category
        }
      : null,
    favorite?.description
      ? {
          label: t('maps.popups.favorite.description'),
          value: favorite.description
        }
      : null,
    favorite?.address
      ? {
          label: t('maps.popups.favorite.address'),
          value: favorite.address
        }
      : null
  ].filter(Boolean)

  return {
    title: getFavoriteName(favorite, pending),
    subtitle: getFavoriteKindLabel({ pending, isArea }),
    iconClass: isArea ? 'pi pi-th-large' : 'pi pi-map-marker',
    rows,
    variant: 'compact'
  }
}
