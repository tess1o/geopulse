export const buildManagedUser = (isolatedUsers, overrides = {}) => {
  const user = isolatedUsers.build(overrides);
  isolatedUsers.createdEmails.add(user.email);
  return user;
};
